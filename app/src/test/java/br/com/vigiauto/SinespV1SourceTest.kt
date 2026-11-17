package br.com.vigiauto

import br.com.vigiauto.data.lookup.LookupResult
import br.com.vigiauto.data.lookup.SinespCredentials
import br.com.vigiauto.data.lookup.SinespV1Protocol
import br.com.vigiauto.data.lookup.SinespV1Source
import br.com.vigiauto.domain.model.QueryStatus
import br.com.vigiauto.domain.model.RestrictionStatus
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SinespV1SourceTest {

    private lateinit var server: MockWebServer

    private val fixedClock = 1700000000000L

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `consulta envia cabecalhos e mapeia resposta`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "plate": "ABC1D23",
                      "model": "FIAT/CRONOS DRIVE 1.0",
                      "year": "2023",
                      "fabricationYear": "2022",
                      "color": "BRANCA",
                      "city": "SAO PAULO",
                      "state": "SP",
                      "chassis": "***1234",
                      "date": "2026-10-20",
                      "alert": [],
                      "hasCortexValidation": true
                    }
                    """.trimIndent()
                )
        )

        val result = source().lookup("ABC1D23")

        assertTrue(result is LookupResult.Success)
        val data = (result as LookupResult.Success).data
        assertEquals("FIAT", data.brand)
        assertEquals("CRONOS DRIVE 1.0", data.model)
        assertEquals("BRANCA", data.color)
        assertEquals(2022, data.manufactureYear)
        assertEquals(2023, data.modelYear)
        assertEquals("SAO PAULO", data.city)
        assertEquals("SP", data.state)
        assertEquals(RestrictionStatus.NONE_IDENTIFIED, data.restrictionStatus)
        assertFalse(data.incomplete)

        val request = server.takeRequest()
        assertEquals("/api/v1/vehicles/ABC1D23", request.path)
        assertEquals("token-de-teste", request.getHeader("Authorization"))
        assertEquals("unique-teste", request.getHeader("UniqueId"))
        assertEquals("-47.8825,-15.7942", request.getHeader("Geolocation"))
        assertEquals("2023-11-14T22:13:20.000Z", request.getHeader("GeolocationTimestamp"))
        assertEquals("100", request.getHeader("GeolocationAccuracy"))
        assertEquals(
            SinespV1Protocol.appEnsurer(fixedClock),
            request.getHeader("AppEnsurer")
        )
        assertEquals("application/json", request.getHeader("Accept"))
    }

    @Test
    fun `sem token nao dispara requisicao`() = runBlocking {
        val result = source(token = null).lookup("ABC1D23")

        assertTrue(result is LookupResult.Failure)
        assertEquals(QueryStatus.UNAVAILABLE, (result as LookupResult.Failure).status)
        assertTrue(result.message!!.contains("Token do gov.br", ignoreCase = true))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `401 vira erro de autenticacao com orientacao`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"message":"Token não informado","status":401}""")
        )

        val result = source().lookup("ABC1D23")

        assertTrue(result is LookupResult.Failure)
        val failure = result as LookupResult.Failure
        assertEquals(QueryStatus.AUTH_ERROR, failure.status)
        assertTrue(failure.message!!.contains("token", ignoreCase = true))
        assertTrue(failure.message!!.contains("renove", ignoreCase = true))
    }

    @Test
    fun `404 vira placa nao encontrada`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"not found"}"""))

        val result = source().lookup("ABC1D23")

        assertEquals(QueryStatus.NOT_FOUND, (result as LookupResult.Failure).status)
    }

    @Test
    fun `429 vira limite de consultas`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429))

        val result = source().lookup("ABC1D23")

        assertEquals(QueryStatus.RATE_LIMITED, (result as LookupResult.Failure).status)
    }

    @Test
    fun `400 de geolocalizacao explica o campo`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"message":"Cabeçalho obrigatório: Geolocation."}""")
        )

        val result = source().lookup("ABC1D23")

        val failure = result as LookupResult.Failure
        assertEquals(QueryStatus.INVALID_RESPONSE, failure.status)
        assertTrue(failure.message!!.contains("localização", ignoreCase = true))
    }

    @Test
    fun `200 sem campos esperados vira resposta invalida`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"message":"ok","status":200}""")
        )

        val result = source().lookup("ABC1D23")

        assertEquals(QueryStatus.INVALID_RESPONSE, (result as LookupResult.Failure).status)
    }

    @Test
    fun `erro 500 e repetido uma vez`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "plate": "ABC1D23",
                      "model": "FIAT/CRONOS",
                      "alert": [],
                      "hasCortexValidation": true
                    }
                    """.trimIndent()
                )
        )

        val result = source().lookup("ABC1D23")

        assertTrue(result is LookupResult.Success)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `teste de conexao valida configuracao sem consultar`() = runBlocking {
        val ok = source().testConnection()
        assertTrue(ok.ok)
        assertEquals(0, server.requestCount)

        val missing = source(token = null).testConnection()
        assertFalse(missing.ok)
        assertTrue(missing.detail.contains("Token do gov.br", ignoreCase = true))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `localizacao invalida usa o ponto neutro`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"plate":"ABC1D23","model":"FIAT/CRONOS","alert":[],"hasCortexValidation":true}""")
        )

        source(geo = "invalida").lookup("ABC1D23")

        val request = server.takeRequest()
        assertEquals(SinespV1Protocol.DEFAULT_GEO, request.getHeader("Geolocation"))
    }

    @Test
    fun `placa invalida nao chega a rede`() = runBlocking {
        val result = source().lookup("ABC")

        assertEquals(QueryStatus.INVALID_RESPONSE, (result as LookupResult.Failure).status)
        assertEquals(0, server.requestCount)
    }

    private fun source(
        token: String? = "token-de-teste",
        geo: String = SinespV1Protocol.DEFAULT_GEO
    ): SinespV1Source = SinespV1Source(
        credentials = { SinespCredentials(token = token, geo = geo, uniqueId = "unique-teste") },
        baseUrl = server.url("/api/v1").toString().trimEnd('/'),
        clockMs = { fixedClock }
    )
}
