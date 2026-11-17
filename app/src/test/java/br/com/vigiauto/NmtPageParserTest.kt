package br.com.vigiauto

import br.com.vigiauto.data.lookup.NmtPageParser
import br.com.vigiauto.domain.model.NmtStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NmtPageParserTest {

    @Test
    fun `reconhece veiculo regular`() {
        val result = NmtPageParser.parse(html("Veículo <b>REGULAR</b>"))

        assertEquals(NmtStatus.REGULAR, result.status)
        assertEquals("REGULAR", result.rawStatus)
        assertTrue(result.rawMessage.orEmpty().contains("Veículo REGULAR"))
    }

    @Test
    fun `reconhece veiculo irregular antes de regular`() {
        val result = NmtPageParser.parse(html("Veículo <b>IRREGULAR</b>"))

        assertEquals(NmtStatus.IRREGULAR, result.status)
        assertEquals("IRREGULAR", result.rawStatus)
    }

    @Test
    fun `reconhece veiculo nao cadastrado com acento`() {
        val result = NmtPageParser.parse(html("Veículo <b>NÃO CADASTRADO</b>"))

        assertEquals(NmtStatus.NOT_REGISTERED, result.status)
        assertEquals("NÃO CADASTRADO", result.rawStatus)
    }

    @Test
    fun `reconhece placa invalida no bloco de erro`() {
        val result = NmtPageParser.parse(
            """
            <html><body>
              <div id="errorMsg" class="alert alert-danger">Placa inválida</div>
            </body></html>
            """.trimIndent()
        )

        assertEquals(NmtStatus.INVALID_PLATE, result.status)
        assertEquals("Placa inválida", result.rawStatus)
    }

    @Test
    fun `mantem texto desconhecido e nao inventa categoria`() {
        val result = NmtPageParser.parse(html("Veículo <b>SEM INFORMAÇÃO</b>"))

        assertEquals(NmtStatus.UNKNOWN, result.status)
        assertEquals("SEM INFORMAÇÃO", result.rawStatus)
        assertNull(NmtStatus.UNKNOWN.categoryLabel())
    }

    @Test
    fun `retorna unknown quando nao ha bloco de resultado`() {
        val result = NmtPageParser.parse("<html><body>Consulta de veículos</body></html>")

        assertEquals(NmtStatus.UNKNOWN, result.status)
        assertNull(result.rawStatus)
    }

    @Test
    fun `ignora bloco de resultado oculto`() {
        val result = NmtPageParser.parse(
            """
            <html><body>
              <div id="retornoMsg" style="display: none">Veículo <b>REGULAR</b></div>
            </body></html>
            """.trimIndent()
        )

        assertEquals(NmtStatus.UNKNOWN, result.status)
    }

    @Test
    fun `rotulos de categoria correspondem ao contrato`() {
        assertEquals(
            "Veículo de aplicativo - Situação regular",
            NmtStatus.REGULAR.categoryLabel()
        )
        assertEquals(
            "Veículo de aplicativo - Situação irregular",
            NmtStatus.IRREGULAR.categoryLabel()
        )
        assertEquals("Veículo particular", NmtStatus.NOT_REGISTERED.categoryLabel())
        assertNull(NmtStatus.INVALID_PLATE.categoryLabel())
        assertNull(NmtStatus.UNAVAILABLE.categoryLabel())
        assertNull(NmtStatus.UNKNOWN.categoryLabel())
    }

    private fun html(status: String): String =
        """
        <html><body>
          <div id="retornoMsg" class="alert alert-cinza text-center" style="display:block">
            <b>UFL 4A55</b><br>
            <div style="float:right; font-size:11px; font-weight:bold">
              10/21/2026 10:44:09 PM&nbsp;&nbsp;
            </div><br>
            $status
          </div>
        </body></html>
        """.trimIndent()
}
