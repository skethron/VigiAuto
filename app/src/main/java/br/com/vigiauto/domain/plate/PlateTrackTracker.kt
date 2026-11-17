package br.com.vigiauto.domain.plate

import kotlin.math.abs
import kotlin.math.max

/**
 * Associa detecções entre quadros e só confirma uma placa depois de leituras
 * consistentes na mesma região visual.
 *
 * Diferente do tracker antigo, que contava apenas strings iguais, este tracker
 * mantém uma caixa por veículo. Duas placas diferentes, mesmo que o OCR erre
 * caracteres, nunca são somadas em uma única confirmação. Uma leitura
 * completamente diferente na mesma caixa reinicia a contagem.
 */
class PlateTrackTracker(
    private val maxAgeMillis: Long = 1_500L,
    private val cooldownMillis: Long = 12_000L,
    private val iouThreshold: Float = 0.15f,
    private val minAgreement: Float = 0.75f
) {

    data class Confirmation(
        val detection: PlateDetection,
        val observations: Int,
        val candidatesInFrame: Int
    )

    private data class Track(
        var box: PlateBox,
        var lastSeen: Long,
        var hits: Int = 0,
        val counts: MutableMap<String, Int> = mutableMapOf(),
        val bestByPlate: MutableMap<String, PlateDetection> = mutableMapOf(),
        var lastConfirmedAt: Long = 0L
    )

    private val tracks = mutableListOf<Track>()
    private val lastConfirmed = HashMap<String, Long>()

    @Synchronized
    fun update(
        detections: List<PlateDetection>,
        now: Long,
        requiredConfirmations: Int,
        motionMode: Boolean
    ): List<Confirmation> {
        val valid = detections.filter { it.normalized != null }
        val candidatesInFrame = valid.mapNotNull { it.normalized }.distinct().size

        tracks.removeAll { now - it.lastSeen > maxAgeMillis }
        lastConfirmed.entries.removeAll { now - it.value > cooldownMillis * 4 }

        val used = MutableList(tracks.size) { false }
        val touched = LinkedHashSet<Track>()

        for (detection in valid.sortedByDescending { it.score }) {
            var bestIndex = -1
            var bestScore = 0f
            for (index in tracks.indices) {
                if (used[index]) continue
                val score = matchScore(tracks[index].box, detection.box)
                if (score > bestScore) {
                    bestScore = score
                    bestIndex = index
                }
            }

            val track = if (bestIndex >= 0 && bestScore >= iouThreshold) {
                used[bestIndex] = true
                tracks[bestIndex]
            } else {
                Track(box = detection.box, lastSeen = now).also {
                    tracks.add(it)
                    // Uma detecção nova não pode ser reutilizada por outra
                    // caixa do mesmo quadro.
                    used.add(true)
                }
            }

            val plate = detection.normalized ?: continue
            // Se a caixa já tinha outra placa visualmente incompatível, não
            // transforme os dois veículos em um só. Comece uma nova contagem.
            if (track.hits > 0 && track.counts.isNotEmpty() &&
                track.counts.keys.none { PlateNormalizer.isVisuallyEquivalent(it, plate) }
            ) {
                track.hits = 0
                track.counts.clear()
                track.bestByPlate.clear()
                track.lastConfirmedAt = 0L
            }

            track.box = detection.box
            track.lastSeen = now
            track.hits += 1
            track.counts[plate] = (track.counts[plate] ?: 0) + 1
            val previousBest = track.bestByPlate[plate]
            if (previousBest == null || detection.score > previousBest.score) {
                track.bestByPlate[plate] = detection
            }
            touched.add(track)
        }

        val required = max(2, requiredConfirmations.coerceAtLeast(1))
        val motionMinimum = max(3, required)
        val confirmations = mutableListOf<Confirmation>()

        for (track in touched) {
            val dominant = track.counts.maxByOrNull { it.value } ?: continue
            val agreement = dominant.value.toFloat() / track.hits
            if (agreement < minAgreement) continue

            val detection = track.bestByPlate[dominant.key] ?: continue
            // Uma leitura excepcionalmente forte, com detector e OCR de alta
            // confiança em todos os caracteres, é confirmada no mesmo quadro:
            // a consulta ainda valida a placa na base antes de gravar. Leituras
            // limítrofes continuam exigindo 2–3 observações.
            val immediateConfirmation = detection.score >= 0.90f &&
                detection.detectorConfidence >= 0.88f &&
                detection.ocrConfidence >= 0.92f &&
                detection.minCharacterConfidence >= 0.60f
            // Em movimento, uma leitura forte e bem segmentada pode ser
            // confirmada em duas observações. Leituras limítrofes continuam
            // exigindo três, preservando a proteção contra falsos positivos.
            val fastConfirmation = detection.score >= 0.82f &&
                detection.ocrConfidence >= 0.72f &&
                detection.minCharacterConfidence >= 0.45f
            val minHits = when {
                immediateConfirmation -> 1
                motionMode && !fastConfirmation -> motionMinimum
                else -> required
            }
            if (track.hits < minHits) continue

            val last = max(track.lastConfirmedAt, lastConfirmed[dominant.key] ?: 0L)
            if (last > 0L && now - last < cooldownMillis) continue

            track.lastConfirmedAt = now
            lastConfirmed[dominant.key] = now
            confirmations.add(
                Confirmation(
                    detection = detection,
                    observations = dominant.value,
                    candidatesInFrame = candidatesInFrame.coerceAtLeast(1)
                )
            )
        }

        return confirmations
    }

    @Synchronized
    fun reset() {
        tracks.clear()
        lastConfirmed.clear()
    }

    /**
     * IoU para o caso normal. Para deslocamento rápido entre quadros, aceita
     * também uma caixa próxima com tamanho e alinhamento compatíveis. Isso é
     * necessário na leitura em movimento, mas não junta placas distantes.
     */
    private fun matchScore(first: PlateBox, second: PlateBox): Float {
        val iou = first.iou(second)
        if (iou >= iouThreshold) return iou

        val maxWidth = max(first.width, second.width)
        val maxHeight = max(first.height, second.height)
        if (maxWidth <= 0f || maxHeight <= 0f) return iou

        val areaRatio = minOf(first.area, second.area) / max(first.area, second.area)
        if (areaRatio < 0.45f) return iou

        val dx = abs((first.left + first.right) / 2f - (second.left + second.right) / 2f)
        val dy = abs(first.centerY - second.centerY)
        if (dx <= maxWidth * 0.80f && dy <= maxHeight * 0.70f) {
            return max(iou, 0.16f)
        }
        return iou
    }
}
