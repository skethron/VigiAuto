package br.com.vigiauto

import br.com.vigiauto.alpr.OcrFallbackPolicy
import br.com.vigiauto.alpr.OcrReading
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrFallbackPolicyTest {

    private fun reading(
        text: String,
        confidence: Float = 0.95f,
        minimum: Float = 0.90f
    ) = OcrReading(text, confidence, minimum)

    @Test
    fun `leitura valida e confiante nao consulta o fallback`() {
        assertFalse(OcrFallbackPolicy.shouldTryFallback(reading("ABC1234")))
        assertFalse(OcrFallbackPolicy.shouldTryFallback(reading("ABC1D23")))
        assertFalse(OcrFallbackPolicy.shouldTryFallback(reading("abc-1234")))
        assertFalse(OcrFallbackPolicy.shouldTryFallback(reading("NIB2212")))
    }

    @Test
    fun `leitura invalida consulta o fallback`() {
        assertTrue(OcrFallbackPolicy.shouldTryFallback(reading("RZ3G66")))
        assertTrue(OcrFallbackPolicy.shouldTryFallback(reading("2J5113")))
        assertTrue(OcrFallbackPolicy.shouldTryFallback(reading("ABC12345")))
        assertTrue(OcrFallbackPolicy.shouldTryFallback(reading("BRASIL")))
    }

    @Test
    fun `leitura valida com caractere fraco consulta o fallback`() {
        assertTrue(OcrFallbackPolicy.shouldTryFallback(reading("NIB2212", minimum = 0.48f)))
        assertFalse(OcrFallbackPolicy.shouldTryFallback(reading("NIB2212", minimum = 0.60f)))
    }

    @Test
    fun `texto vazio nao gasta uma segunda inferencia`() {
        assertFalse(OcrFallbackPolicy.shouldTryFallback(null))
        assertFalse(OcrFallbackPolicy.shouldTryFallback(reading("")))
        assertFalse(OcrFallbackPolicy.shouldTryFallback(reading("   ")))
    }

    @Test
    fun `fallback assume quando a leitura principal e invalida`() {
        assertTrue(
            OcrFallbackPolicy.preferFallback(
                reading("RZ3G66", minimum = 0.40f),
                reading("ERZ3G63")
            )
        )
        assertFalse(
            OcrFallbackPolicy.preferFallback(
                reading("RZ3G66", minimum = 0.40f),
                reading("RZ3G66")
            )
        )
    }

    @Test
    fun `fallback corrige um unico caractere nao visual`() {
        assertTrue(
            OcrFallbackPolicy.preferFallback(
                reading("NIB2212", confidence = 0.87f, minimum = 0.48f),
                reading("NIB7212", confidence = 0.93f, minimum = 0.75f)
            )
        )
    }

    @Test
    fun `fallback nao desfaz confusao visual nem varias trocas`() {
        assertFalse(
            OcrFallbackPolicy.preferFallback(
                reading("STE7129", confidence = 0.93f, minimum = 0.45f),
                reading("STE7I29", confidence = 0.99f, minimum = 0.99f)
            )
        )
        assertFalse(
            OcrFallbackPolicy.preferFallback(
                reading("ABC1D23", confidence = 0.90f, minimum = 0.40f),
                reading("XYZ9Z99", confidence = 0.99f, minimum = 0.99f)
            )
        )
    }

    @Test
    fun `fallback nao assume com confianca media baixa`() {
        assertFalse(
            OcrFallbackPolicy.preferFallback(
                reading("NIB2212", confidence = 0.87f, minimum = 0.48f),
                reading("NIB7212", confidence = 0.70f, minimum = 0.70f)
            )
        )
    }
}
