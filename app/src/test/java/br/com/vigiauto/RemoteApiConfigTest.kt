package br.com.vigiauto

import br.com.vigiauto.data.lookup.RemoteApiConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteApiConfigTest {

    @Test
    fun `extrai apiPlaca do remote config`() {
        val template = RemoteApiConfig.parseApiTemplate(
            """{"apiPlaca":"https://placa.costaw.com.br/api?placa=placaaqui&token=abc"}"""
        )
        assertEquals("https://placa.costaw.com.br/api?placa=placaaqui&token=abc", template)
    }

    @Test
    fun `rejeita remote config sem apiPlaca ou http`() {
        assertNull(RemoteApiConfig.parseApiTemplate("""{"showAds":true}"""))
        assertNull(RemoteApiConfig.parseApiTemplate("""{"apiPlaca":"http://exemplo.test/api"}"""))
        assertNull(RemoteApiConfig.parseApiTemplate("""{"apiPlaca":"não é url"}"""))
    }

    @Test
    fun `substitui placa preservando token`() {
        val url = RemoteApiConfig.forPlate(
            "https://placa.costaw.com.br/api?placa=placaaqui&token=segredo",
            "ABC1D23"
        )
        val parsed = url!!.toHttpUrlOrNull()!!
        assertEquals("abc1d23", parsed.queryParameter("placa")?.lowercase())
        assertEquals("segredo", parsed.queryParameter("token"))
        assertEquals("placa.costaw.com.br", parsed.host)
    }

    @Test
    fun `nao monta url http sem tls`() {
        assertNull(RemoteApiConfig.forPlate("http://placa.costaw.com.br/api?placa=placaaqui", "ABC1234"))
        assertFalse(RemoteApiConfig.isValidTemplate("http://placa.costaw.com.br/api"))
        assertTrue(RemoteApiConfig.isValidTemplate("https://placa.costaw.com.br/api"))
    }

    @Test
    fun `extrai mensagem de erro 404`() {
        assertEquals(
            "Veículo não encontrado",
            RemoteApiConfig.messageFrom("""{"message":"Veículo não encontrado"}""")
        )
    }
}
