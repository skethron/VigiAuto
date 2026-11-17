package br.com.vigiauto

import br.com.vigiauto.domain.plate.PlateBox
import br.com.vigiauto.domain.plate.PlateDetection
import br.com.vigiauto.domain.plate.PlateTrackTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateTrackTrackerTest {

    private var now = 1_000L

    private fun detection(
        plate: String,
        left: Float = 100f,
        top: Float = 100f,
        confidence: Float = 0.90f
    ) = PlateDetection(
        box = PlateBox(left, top, left + 120f, top + 40f),
        detectorConfidence = confidence,
        rawText = plate,
        normalized = plate,
        ocrConfidence = confidence,
        minCharacterConfidence = confidence
    )

    @Test
    fun `confirma somente depois de leituras na mesma caixa`() {
        val tracker = PlateTrackTracker()
        assertTrue(tracker.update(listOf(detection("ABC1234")), now, 2, false).isEmpty())
        now += 120
        val result = tracker.update(listOf(detection("ABC1234", left = 104f)), now, 2, false)
        assertEquals(1, result.size)
        assertEquals("ABC1234", result.first().detection.normalized)
    }

    @Test
    fun `nao junta duas placas em caixas diferentes`() {
        val tracker = PlateTrackTracker()
        val first = listOf(
            detection("ABC1234", left = 40f, top = 60f),
            detection("XYZ9876", left = 700f, top = 60f)
        )
        assertTrue(tracker.update(first, now, 2, false).isEmpty())
        now += 120
        val result = tracker.update(first, now, 2, false)
        assertEquals(2, result.size)
        assertEquals(setOf("ABC1234", "XYZ9876"), result.map { it.detection.normalized }.toSet())
    }

    @Test
    fun `leitura divergente nao confirma uma placa errada`() {
        val tracker = PlateTrackTracker()
        val sequence = listOf("ABC1234", "XYZ9876", "ABC1234", "XYZ9876")
        var confirmations = 0
        sequence.forEachIndexed { index, plate ->
            now += 120
            confirmations += tracker.update(listOf(detection(plate)), now, 2, false).size
            if (index < sequence.lastIndex) assertTrue(confirmations == 0)
        }
        assertEquals(0, confirmations)
    }

    @Test
    fun `consenso exige maioria qualificada e depois aceita leitura estavel`() {
        val tracker = PlateTrackTracker()
        var confirmations = 0
        listOf("ABC1234", "XYZ9876", "ABC1234").forEach {
            now += 120
            confirmations += tracker.update(listOf(detection(it)), now, 2, false).size
        }
        assertEquals(0, confirmations)

        now += 120
        val result = tracker.update(listOf(detection("ABC1234")), now, 2, false)
        assertEquals(1, result.size)
        assertEquals("ABC1234", result.first().detection.normalized)
    }

    @Test
    fun `cooldown impede confirmacoes repetidas da mesma placa`() {
        val tracker = PlateTrackTracker()
        tracker.update(listOf(detection("ABC1234")), now, 2, false)
        now += 120
        assertEquals(1, tracker.update(listOf(detection("ABC1234")), now, 2, false).size)

        now += 120
        assertTrue(tracker.update(listOf(detection("ABC1234")), now, 2, false).isEmpty())

        now += 13_000
        assertTrue(tracker.update(listOf(detection("ABC1234")), now, 2, false).isEmpty())
        now += 120
        assertEquals(1, tracker.update(listOf(detection("ABC1234")), now, 2, false).size)
    }

    @Test
    fun `leitura excepcionalmente forte confirma no mesmo quadro`() {
        val tracker = PlateTrackTracker()
        val result = tracker.update(
            listOf(detection("ABC1234", confidence = 0.96f)),
            now,
            2,
            true
        )
        assertEquals(1, result.size)
        assertEquals("ABC1234", result.first().detection.normalized)
    }

    @Test
    fun `alta confianca em movimento confirma em duas leituras`() {
        val tracker = PlateTrackTracker()
        assertTrue(tracker.update(listOf(detection("ABC1234")), now, 2, true).isEmpty())
        now += 120
        val result = tracker.update(listOf(detection("ABC1234", left = 104f)), now, 2, true)
        assertEquals(1, result.size)
        assertEquals("ABC1234", result.first().detection.normalized)
    }

    @Test
    fun `leitura limítrofe em movimento continua exigindo tres`() {
        val tracker = PlateTrackTracker()
        assertTrue(
            tracker.update(listOf(detection("ABC1234", confidence = 0.60f)), now, 2, true)
                .isEmpty()
        )
        now += 120
        assertTrue(
            tracker.update(
                listOf(detection("ABC1234", left = 104f, confidence = 0.60f)),
                now,
                2,
                true
            ).isEmpty()
        )
        now += 120
        val result = tracker.update(
            listOf(detection("ABC1234", left = 108f, confidence = 0.60f)),
            now,
            2,
            true
        )
        assertEquals(1, result.size)
    }

    @Test
    fun `caixa que desaparece reinicia o rastreamento`() {
        val tracker = PlateTrackTracker()
        tracker.update(listOf(detection("ABC1234")), now, 2, false)
        now += 3_000
        assertTrue(tracker.update(listOf(detection("ABC1234")), now, 2, false).isEmpty())
        now += 120
        assertEquals(1, tracker.update(listOf(detection("ABC1234")), now, 2, false).size)
    }
}
