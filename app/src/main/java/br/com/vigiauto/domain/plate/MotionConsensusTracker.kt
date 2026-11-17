package br.com.vigiauto.domain.plate

import java.util.ArrayDeque
import kotlin.math.abs

/**
 * Fallback para leitura em movimento.
 *
 * Vibração e deslocamento fazem o OCR trocar um caractere entre quadros. Este
 * tracker agrupa leituras próximas (distância de edição <= 1), exige pelo menos
 * três observações e monta um consenso posição a posição com maioria qualificada.
 *
 * Regras de segurança:
 * - nunca mistura placas com mais de um caractere de diferença;
 * - exige 3+ leituras na janela;
 * - exige 60% de concordância em cada posição;
 * - aplica cooldown para não consultar a mesma placa repetidamente.
 */
class MotionConsensusTracker(
    private val windowMillis: Long = 3_000L,
    private val minObservations: Int = 3,
    private val minAgreement: Float = 0.6f,
    private val cooldownMillis: Long = 4_000L,
    private val clock: () -> Long = System::currentTimeMillis
) {

    private data class Observation(val plate: String, val seenAt: Long)

    private val observations = ArrayDeque<Observation>()
    private var lastConfirmedPlate: String? = null
    private var lastConfirmedAt: Long = 0L

    fun reset() {
        observations.clear()
        lastConfirmedPlate = null
        lastConfirmedAt = 0L
    }

    /**
     * Marca uma placa já confirmada pelo caminho exato para evitar que o
     * consenso a confirme novamente em seguida.
     */
    fun markConfirmed(plate: String) {
        lastConfirmedPlate = plate
        lastConfirmedAt = clock()
        observations.removeAll { it.plate == plate }
    }

    fun offer(plate: String): String? {
        val now = clock()
        observations.removeAll { now - it.seenAt > windowMillis }
        observations.addLast(Observation(plate, now))

        if (observations.size < minObservations) return null
        if (lastConfirmedPlate == plate && now - lastConfirmedAt < cooldownMillis) return null

        val anchor = observations.groupingBy { it.plate }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: return null

        val cluster = observations.filter { editDistance(it.plate, anchor) <= 1 }
        if (cluster.size < minObservations) return null

        val consensus = buildConsensus(cluster.map { it.plate }) ?: return null
        if (!PlatePattern.isPlate(consensus)) return null
        if (editDistance(consensus, anchor) > 1) return null

        if (lastConfirmedPlate == consensus && now - lastConfirmedAt < cooldownMillis) return null

        observations.clear()
        lastConfirmedPlate = consensus
        lastConfirmedAt = now
        return consensus
    }

    private fun buildConsensus(plates: List<String>): String? {
        if (plates.isEmpty()) return null
        val length = plates.first().length
        if (plates.any { it.length != length }) return null

        val consensus = StringBuilder(length)
        for (position in 0 until length) {
            val counts = plates.groupingBy { it[position] }.eachCount()
            val (character, count) = counts.maxByOrNull { it.value } ?: return null
            if (count.toFloat() / plates.size < minAgreement) return null
            consensus.append(character)
        }
        return consensus.toString()
    }

    private fun editDistance(first: String, second: String): Int {
        if (first.length != second.length) return Int.MAX_VALUE
        var differences = 0
        for (index in first.indices) {
            if (first[index] != second[index]) {
                differences++
                if (differences > 1) return differences
            }
        }
        return abs(differences)
    }
}
