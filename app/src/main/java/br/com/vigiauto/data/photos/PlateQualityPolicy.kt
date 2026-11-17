package br.com.vigiauto.data.photos

import br.com.vigiauto.domain.model.PhotoQuality
import br.com.vigiauto.domain.model.PlateQuality

data class PlateQualityAssessment(
    val photoQuality: PhotoQuality,
    val plateQuality: PlateQuality,
    val needsReview: Boolean
)

/**
 * Decide apenas como sinalizar a captura. Nunca altera o recorte para
 * "melhorar" caracteres: placas desfocadas ou pequenas continuam exatamente
 * como foram fotografadas.
 */
object PlateQualityPolicy {

    private const val MIN_DETECTOR_CONFIDENCE = 0.50f
    private const val MIN_OCR_CONFIDENCE = 0.55f
    private const val MIN_PLATE_WIDTH_PX = 48f
    private const val MIN_SHARPNESS = 0.035f

    fun assess(
        detectorConfidence: Float,
        ocrConfidence: Float,
        plateWidthPx: Float,
        cropSharpness: Float?,
        candidatesInFrame: Int,
        cropAvailable: Boolean,
        textMismatch: Boolean = false
    ): PlateQualityAssessment {
        val lowConfidence = detectorConfidence < MIN_DETECTOR_CONFIDENCE ||
            ocrConfidence < MIN_OCR_CONFIDENCE
        val smallPlate = plateWidthPx < MIN_PLATE_WIDTH_PX
        val blurry = cropSharpness != null && cropSharpness < MIN_SHARPNESS
        val multipleVehicles = candidatesInFrame > 1
        val needsReview = !cropAvailable || lowConfidence || smallPlate || blurry ||
            multipleVehicles || textMismatch

        return PlateQualityAssessment(
            photoQuality = if (needsReview) PhotoQuality.LIMITED else PhotoQuality.GOOD,
            plateQuality = when {
                !cropAvailable -> PlateQuality.UNAVAILABLE
                needsReview -> PlateQuality.LOW
                else -> PlateQuality.GOOD
            },
            needsReview = needsReview
        )
    }
}
