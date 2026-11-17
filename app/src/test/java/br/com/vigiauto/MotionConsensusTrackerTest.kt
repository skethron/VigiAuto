package br.com.vigiauto

import br.com.vigiauto.domain.plate.MotionConsensusTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MotionConsensusTrackerTest {

    private var now = 1_000L

    private fun tracker() = MotionConsensusTracker(clock = { now })

    @Test
    fun `confirma consenso com tres leituras variando um caractere`() {
        val tracker = tracker()
        assertNull(tracker.offer("ABC1D23"))
        assertNull(tracker.offer("ABC1D28"))
        assertEquals("ABC1D23", tracker.offer("ABC1D23"))
    }

    @Test
    fun `nao confirma com apenas duas leituras`() {
        val tracker = tracker()
        assertNull(tracker.offer("ABC1D23"))
        now += 100
        assertNull(tracker.offer("ABC1D23"))
    }

    @Test
    fun `nao mistura placas com mais de um caractere de diferenca`() {
        val tracker = tracker()
        assertNull(tracker.offer("ABC1D23"))
        now += 100
        assertNull(tracker.offer("XYZ4E56"))
        now += 100
        assertNull(tracker.offer("ABC1D23"))
        now += 100
        assertEquals("ABC1D23", tracker.offer("ABC1D23"))
    }

    @Test
    fun `cooldown evita confirmacao repetida imediata`() {
        val tracker = tracker()
        tracker.offer("ABC1D23")
        tracker.offer("ABC1D23")
        assertEquals("ABC1D23", tracker.offer("ABC1D23"))

        assertNull(tracker.offer("ABC1D23"))
        now += 100
        assertNull(tracker.offer("ABC1D23"))

        now += 5_000
        tracker.offer("ABC1D23")
        tracker.offer("ABC1D23")
        assertEquals("ABC1D23", tracker.offer("ABC1D23"))
    }

    @Test
    fun `descarta observacoes fora da janela`() {
        val tracker = tracker()
        tracker.offer("ABC1D23")
        now += 4_000
        tracker.offer("ABC1D28")
        now += 100
        assertNull(tracker.offer("ABC1D23"))
    }

    @Test
    fun `markConfirmed limpa observacoes da placa`() {
        val tracker = tracker()
        tracker.offer("ABC1D23")
        tracker.offer("ABC1D23")
        tracker.markConfirmed("ABC1D23")
        now += 100
        assertNull(tracker.offer("ABC1D23"))
    }
}
