package br.com.vigiauto.alpr

import br.com.vigiauto.domain.plate.PlateNormalizer

/**
 * Decide quando a leitura do OCR principal precisa de uma segunda opinião.
 *
 * A inferência extra acontece em dois casos:
 * - o texto não forma uma placa brasileira válida; ou
 * - o texto é válido, mas o caractere menos confiável ficou abaixo de 50%.
 *
 * No segundo caso, o modelo anterior só assume quando a leitura dele é válida,
 * tem confiança média alta e difere em exatamente um caractere não equivalente
 * visualmente (por exemplo, 7 trocado por 2). Isso evita desfazer correções
 * boas do modelo novo em diferenças como I/1, O/0, U/V, C/G e Q/O.
 */
internal object OcrFallbackPolicy {

    private const val PRIMARY_RECHECK_MIN_CONFIDENCE = 0.50f
    private const val FALLBACK_OVERRIDE_MIN_CONFIDENCE = 0.90f

    fun shouldTryFallback(primary: OcrReading?): Boolean {
        if (primary == null || primary.rawText.isBlank()) return false
        if (PlateNormalizer.normalize(primary.rawText) == null) return true
        return primary.minCharacterConfidence < PRIMARY_RECHECK_MIN_CONFIDENCE
    }

    fun preferFallback(primary: OcrReading?, fallback: OcrReading?): Boolean {
        if (fallback == null) return false
        val fallbackPlate = PlateNormalizer.normalize(fallback.rawText) ?: return false
        val primaryPlate = primary?.let { PlateNormalizer.normalize(it.rawText) }
            ?: return true

        if (primaryPlate == fallbackPlate) return false
        if (fallback.confidence < FALLBACK_OVERRIDE_MIN_CONFIDENCE) return false

        val primaryClean = PlateNormalizer.clean(primaryPlate)
        val fallbackClean = PlateNormalizer.clean(fallbackPlate)
        if (primaryClean.length != 7 || fallbackClean.length != 7) return false

        val differences = primaryClean.indices.count {
            primaryClean[it] != fallbackClean[it]
        }
        if (differences != 1) return false
        return !PlateNormalizer.isVisuallyEquivalent(primaryClean, fallbackClean)
    }
}
