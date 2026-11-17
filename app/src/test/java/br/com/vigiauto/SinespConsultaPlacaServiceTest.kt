package br.com.vigiauto

import br.com.vigiauto.data.lookup.ConnectionTestResult
import br.com.vigiauto.data.lookup.LookupResult
import br.com.vigiauto.data.lookup.SinespConsultaPlacaService
import br.com.vigiauto.data.lookup.SinespSource
import br.com.vigiauto.domain.model.QueryStatus
import br.com.vigiauto.domain.model.RestrictionStatus
import br.com.vigiauto.domain.model.VehicleData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SinespConsultaPlacaServiceTest {

    @Test
    fun `sem fonte configurada retorna indisponivel sem consultar`() = runBlocking {
        val result = SinespConsultaPlacaService().lookup("ABC1D23")

        assertTrue(result is LookupResult.Failure)
        assertEquals(QueryStatus.UNAVAILABLE, (result as LookupResult.Failure).status)
        assertTrue(result.message!!.contains("Sinesp", ignoreCase = true))
    }

    @Test
    fun `sem fonte configurada o teste de conexao nao faz requisicao`() = runBlocking {
        val result = SinespConsultaPlacaService().testConnection()

        assertFalse(result.ok)
        assertTrue(result.detail.contains("Sinesp", ignoreCase = true))
    }

    @Test
    fun `delega para a fonte injetada`() = runBlocking {
        val data = VehicleData(
            brand = "Teste",
            model = "Fonte",
            restrictionStatus = RestrictionStatus.NONE_IDENTIFIED,
            sourceName = "Sinesp de teste"
        )
        val source = object : SinespSource {
            override suspend fun lookup(plate: String): LookupResult {
                assertEquals("ABC1D23", plate)
                return LookupResult.Success(data)
            }

            override suspend fun testConnection(): ConnectionTestResult =
                ConnectionTestResult(true, "fonte acessível")
        }
        val service = SinespConsultaPlacaService(source)

        val result = service.lookup("ABC1D23")
        assertTrue(result is LookupResult.Success)
        assertEquals(data, (result as LookupResult.Success).data)
        assertTrue(service.testConnection().ok)
    }
}
