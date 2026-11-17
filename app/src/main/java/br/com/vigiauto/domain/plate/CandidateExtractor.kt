package br.com.vigiauto.domain.plate

import br.com.vigiauto.domain.model.PlateCandidate
import kotlin.math.max
import kotlin.math.min

/**
 * Extrai candidatos de placa a partir do texto reconhecido.
 *
 * Além de linhas e elementos individuais, combina:
 * - elementos próximos na mesma linha (placas fragmentadas pelo OCR);
 * - duas linhas verticalmente adjacentes (placas de motocicleta no layout
 *   Mercosul de duas linhas, ex.: "SUK" + "6E51").
 */
object CandidateExtractor {

    private const val MAX_ELEMENTS_PER_WINDOW = 3

    fun extract(document: OcrDocument): List<PlateCandidate> {
        val found = LinkedHashMap<String, PlateCandidate>()

        fun consider(raw: String, box: PlateBox, lineCount: Int, parts: Int) {
            val alphanumeric = raw.count { it.isLetterOrDigit() }
            if (alphanumeric !in 7..10) return
            val normalized = PlateNormalizer.normalize(raw) ?: return
            val confidence = confidence(raw, normalized, box, lineCount, parts)
            val candidate = PlateCandidate(
                rawText = raw,
                normalized = normalized,
                confidence = confidence,
                box = box,
                lineCount = lineCount
            )
            val previous = found[normalized]
            if (previous == null || confidence > previous.confidence) {
                found[normalized] = candidate
            }
        }

        document.lines.forEach { line ->
            consider(line.text, line.box, 1, 1)

            line.elements.forEach { element ->
                consider(element.text, element.box, 1, 1)
            }

            for (start in line.elements.indices) {
                val joined = StringBuilder()
                var union = PlateBox.EMPTY
                for (offset in 0 until MAX_ELEMENTS_PER_WINDOW) {
                    val index = start + offset
                    if (index >= line.elements.size) break
                    val element = line.elements[index]
                    joined.append(element.text)
                    union = union.union(element.box)
                    if (offset >= 1) {
                        consider(joined.toString(), union, 1, offset + 1)
                    }
                }
            }
        }

        for (i in document.lines.indices) {
            val upperLimit = minOf(i + 3, document.lines.size)
            for (j in i + 1 until upperLimit) {
                val first = document.lines[i]
                val second = document.lines[j]
                if (!verticallyAdjacent(first.box, second.box)) continue
                val union = first.box.union(second.box)
                consider(first.text + second.text, union, 2, 2)
                consider(second.text + first.text, union, 2, 2)
            }
        }

        return found.values.sortedByDescending { it.confidence }
    }

    fun extract(document: OcrDocument, minConfidence: Float): List<PlateCandidate> =
        extract(document).filter { it.confidence >= minConfidence }

    private fun verticallyAdjacent(first: PlateBox, second: PlateBox): Boolean {
        if (first.height <= 0f || second.height <= 0f) return false
        val upper = if (first.centerY <= second.centerY) first else second
        val lower = if (upper === first) second else first
        val gap = lower.top - upper.bottom
        val averageHeight = (upper.height + lower.height) / 2f
        if (gap > averageHeight * 1.4f) return false
        if (gap < -averageHeight * 0.8f) return false
        val overlap = min(upper.right, lower.right) - max(upper.left, lower.left)
        val smallestWidth = min(upper.width, lower.width)
        return smallestWidth > 0f && overlap / smallestWidth >= 0.3f
    }

    private fun confidence(raw: String, normalized: String, box: PlateBox, lineCount: Int, parts: Int): Float {
        var confidence = 0.55f
        val cleaned = PlateNormalizer.clean(raw)

        confidence += if (cleaned == normalized) 0.18f else 0.10f
        if (parts <= 2) confidence += 0.05f
        if (cleaned.length == 7) confidence += 0.04f

        val ratio = if (box.height > 0f) box.width / box.height else 0f
        if (lineCount == 1) {
            if (ratio in 2.0f..6.0f) confidence += 0.10f
        } else {
            // Placas de moto: retângulo mais alto, proporção menor.
            if (ratio in 0.6f..3.2f) confidence += 0.08f
        }

        return confidence.coerceIn(0f, 0.99f)
    }
}
