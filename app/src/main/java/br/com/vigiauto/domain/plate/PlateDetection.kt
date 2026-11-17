package br.com.vigiauto.domain.plate

/**
 * Uma placa localizada pelo detector visual e lida pelo OCR especializado.
 *
 * [normalized] pode ser nulo quando o detector encontrou uma região de placa,
 * mas o OCR não produziu uma sequência brasileira válida. Nesse caso o
 * analisador pode tentar uma segunda leitura no recorte.
 */
data class PlateDetection(
    val box: PlateBox,
    val detectorConfidence: Float,
    val rawText: String,
    val normalized: String?,
    val ocrConfidence: Float,
    val minCharacterConfidence: Float
) {
    /** Confiança combinada; o detector é o filtro principal contra falsos positivos. */
    val score: Float
        get() = (detectorConfidence * 0.60f + ocrConfidence * 0.40f).coerceIn(0f, 1f)
}
