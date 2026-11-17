package br.com.vigiauto

import br.com.vigiauto.data.lookup.SinespAuthClient
import br.com.vigiauto.data.lookup.SinespLoginResult
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SinespAuthClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(): SinespAuthClient = SinespAuthClient(
        client = OkHttpClient(),
        baseUrl = server.url("/api/v1").toString(),
        clockMs = { 1_700_000_000_000L }
    )

    private fun response(code: Int, body: String): MockResponse = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    @Test
    fun `troca code por token e valida no me`() = runBlocking {
        server.enqueue(response(200, "\"token-abc\""))
        server.enqueue(response(200, "{}"))

        val result = client().exchangeCode("abc123")

        assertTrue(result is SinespLoginResult.Success)
        val success = result as SinespLoginResult.Success
        assertEquals("token-abc", success.token)
        assertNull(success.warning)

        val tokenRequest = server.takeRequest()
        assertEquals("POST", tokenRequest.method)
        assertEquals("/api/v1/auth/token?code=abc123", tokenRequest.path)
        assertEquals("application/json", tokenRequest.getHeader("Accept"))
        assertEquals(
            "br.gov.sinesp.cidadao.android/4.0.0",
            tokenRequest.getHeader("User-Agent")
        )
        assertNotNull(tokenRequest.getHeader("AppEnsurer"))
        assertNull(tokenRequest.getHeader("Authorization"))

        val meRequest = server.takeRequest()
        assertEquals("GET", meRequest.method)
        assertEquals("/api/v1/me", meRequest.path)
        assertEquals("token-abc", meRequest.getHeader("Authorization"))
    }

    @Test
    fun `remove prefixo Bearer antes de validar e salvar`() = runBlocking {
        server.enqueue(response(200, "\"Bearer token-abc\""))
        server.enqueue(response(200, "{}"))

        val result = client().exchangeCode("abc123")

        assertTrue(result is SinespLoginResult.Success)
        assertEquals("token-abc", (result as SinespLoginResult.Success).token)
        server.takeRequest()
        assertEquals("token-abc", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `aceita token devolvido em objeto json`() = runBlocking {
        server.enqueue(response(200, """{"access_token":"token-obj"}"""))
        server.enqueue(response(200, "{}"))

        val result = client().exchangeCode("abc123")

        assertTrue(result is SinespLoginResult.Success)
        assertEquals("token-obj", (result as SinespLoginResult.Success).token)
    }

    @Test
    fun `rejeita quando o me recusa o token`() = runBlocking {
        server.enqueue(response(200, "\"token-abc\""))
        server.enqueue(response(401, """{"message":"unauthorized"}"""))

        val result = client().exchangeCode("abc123")

        assertTrue(result is SinespLoginResult.Failure)
        val failure = result as SinespLoginResult.Failure
        assertTrue(failure.message.contains("recusou", ignoreCase = true))
    }

    @Test
    fun `salva token com aviso quando o me esta indisponivel`() = runBlocking {
        server.enqueue(response(200, "\"token-abc\""))
        server.enqueue(response(503, "{}"))

        val result = client().exchangeCode("abc123")

        assertTrue(result is SinespLoginResult.Success)
        val success = result as SinespLoginResult.Success
        assertEquals("token-abc", success.token)
        assertNotNull(success.warning)
    }

    @Test
    fun `falha quando a troca do code e recusada sem vazar o corpo`() = runBlocking {
        server.enqueue(response(401, """{"message":"invalid code"}"""))

        val result = client().exchangeCode("abc123")

        assertTrue(result is SinespLoginResult.Failure)
        val failure = result as SinespLoginResult.Failure
        assertTrue(failure.message.contains("HTTP 401"))
        assertFalse(failure.message.contains("invalid code"))
    }
}
