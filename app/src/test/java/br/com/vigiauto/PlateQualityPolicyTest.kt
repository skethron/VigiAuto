package br.com.vigiauto

import br.com.vigiauto.data.photos.PlateQualityPolicy
import br.com.vigiauto.domain.model.PhotoQuality
import br.com.vigiauto.domain.model.PlateQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateQualityPolicyTest {

    @Test
    fun capturaLegivelNaoExigeVerificacao() {
        val assessment = PlateQualityPolicy.assess(
            detectorConfidence = 0.92f,
            ocrConfidence = 0.88f,
            plateWidthPx = 180f,
            cropSharpness = 0.12f,
            candidatesInFrame = 1,
            cropAvailable = true
        )

        assertEquals(PhotoQuality.GOOD, assessment.photoQuality)
        assertEquals(PlateQuality.GOOD, assessment.plateQuality)
        assertFalse(assessment.needsReview)
    }

    @Test
    fun ocrComBaixaConfiancaSinalizaVerificacaoSemAlterarRecorte() {
        val assessment = PlateQualityPolicy.assess(
            detectorConfidence = 0.90f,
            ocrConfidence = 0.41f,
            plateWidthPx = 150f,
            cropSharpness = 0.10f,
            candidatesInFrame = 1,
            cropAvailable = true
        )

        assertEquals(PhotoQuality.LIMITED, assessment.photoQuality)
        assertEquals(PlateQuality.LOW, assessment.plateQuality)
        assertTrue(assessment.needsReview)
    }

    @Test
    fun placaPequenaOuDesfocadaSinalizaBaixaQualidade() {
        val assessment = PlateQualityPolicy.assess(
            detectorConfidence = 0.80f,
            ocrConfidence = 0.80f,
            plateWidthPx = 32f,
            cropSharpness = 0.01f,
            candidatesInFrame = 1,
            cropAvailable = true
        )

        assertEquals(PlateQuality.LOW, assessment.plateQuality)
        assertTrue(assessment.needsReview)
    }

    @Test
    fun variosVeiculosNoQuadroLimitamAConfiancaDaFoto() {
        val assessment = PlateQualityPolicy.assess(
            detectorConfidence = 0.99f,
            ocrConfidence = 0.99f,
            plateWidthPx = 220f,
            cropSharpness = 0.20f,
            candidatesInFrame = 2,
            cropAvailable = true
        )

        assertEquals(PhotoQuality.LIMITED, assessment.photoQuality)
        assertEquals(PlateQuality.LOW, assessment.plateQuality)
        assertTrue(assessment.needsReview)
    }

    @Test
    fun semRecorteMantemFotoComQualidadeLimitada() {
        val assessment = PlateQualityPolicy.assess(
            detectorConfidence = 0.99f,
            ocrConfidence = 0.99f,
            plateWidthPx = 0f,
            cropSharpness = null,
            candidatesInFrame = 1,
            cropAvailable = false
        )

        assertEquals(PhotoQuality.LIMITED, assessment.photoQuality)
        assertEquals(PlateQuality.UNAVAILABLE, assessment.plateQuality)
        assertTrue(assessment.needsReview)
    }
}
