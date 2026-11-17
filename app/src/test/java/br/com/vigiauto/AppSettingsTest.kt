package br.com.vigiauto

import br.com.vigiauto.data.prefs.AppSettings
import br.com.vigiauto.domain.model.LookupMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppSettingsTest {

    @Test
    fun `sinesp e o modo padrao da versao 1_5_0`() {
        assertEquals(LookupMode.SINESP, AppSettings().lookupMode)
    }

    @Test
    fun `token do sinesp nao vem configurado por padrao`() {
        assertFalse(AppSettings().hasSinespToken)
    }

    @Test
    fun `localizacao padrao do sinesp e um ponto neutro em brasilia`() {
        assertEquals("-47.8825,-15.7942", AppSettings().sinespGeo)
    }

    @Test
    fun `cadencia padrao acelera a leitura sem passar do limite`() {
        assertEquals(12, AppSettings().analysisFps)
        assertEquals(83L, AppSettings().analysisIntervalMs)
        assertEquals(66L, AppSettings(analysisFps = 15).analysisIntervalMs)
        assertEquals(66L, AppSettings(analysisFps = 99).analysisIntervalMs)
    }
}
