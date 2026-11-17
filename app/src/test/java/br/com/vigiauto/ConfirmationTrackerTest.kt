package br.com.vigiauto

import br.com.vigiauto.domain.plate.ConfirmationTracker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmationTrackerTest {

    private var now = 1_000L

    private fun tracker(required: Int = 2) = ConfirmationTracker(clock = { now })

    @Test
    fun `confirma somente apos leituras repetidas`() {
        val tracker = tracker()
        assertFalse(tracker.offer("ABC1234", 2))
        assertTrue(tracker.offer("ABC1234", 2))
    }

    @Test
    fun `confirma imediatamente quando exigido apenas uma leitura`() {
        val tracker = tracker()
        assertTrue(tracker.offer("ABC1234", 1))
    }

    @Test
    fun `janela expirada reinicia a contagem`() {
        val tracker = tracker()
        assertFalse(tracker.offer("ABC1234", 2))
        now += 5_000
        assertFalse(tracker.offer("ABC1234", 2))
        assertTrue(tracker.offer("ABC1234", 2))
    }

    @Test
    fun `placas diferentes nao interferem entre si`() {
        val tracker = tracker()
        assertFalse(tracker.offer("ABC1234", 2))
        assertFalse(tracker.offer("XYZ9876", 2))
        assertTrue(tracker.offer("ABC1234", 2))
    }

    @Test
    fun `apos confirmar comeca nova contagem`() {
        val tracker = tracker()
        assertFalse(tracker.offer("ABC1234", 2))
        assertTrue(tracker.offer("ABC1234", 2))
        assertFalse(tracker.offer("ABC1234", 2))
    }

    @Test
    fun `reset limpa estados`() {
        val tracker = tracker()
        tracker.offer("ABC1234", 2)
        tracker.reset()
        assertFalse(tracker.offer("ABC1234", 2))
    }
}
