package br.com.vigiauto.domain.plate

/**
 * Exige leituras repetidas da mesma placa em uma janela de tempo antes de
 * confirmá-la. Evita consultas causadas por erros isolados de OCR.
 */
class ConfirmationTracker(
    private val windowMillis: Long = 2_500L,
    private val clock: () -> Long = System::currentTimeMillis
) {

    private data class State(var count: Int, var firstSeen: Long)

    private val states = HashMap<String, State>()

    fun reset() {
        states.clear()
    }

    fun offer(normalizedPlate: String, requiredConfirmations: Int): Boolean {
        val now = clock()
        states.entries.removeAll { now - it.value.firstSeen > windowMillis }

        val required = requiredConfirmations.coerceAtLeast(1)
        val current = states[normalizedPlate]
        val state: State
        if (current == null) {
            state = State(count = 1, firstSeen = now)
            states[normalizedPlate] = state
        } else {
            current.count++
            state = current
        }

        if (state.count >= required) {
            states.remove(normalizedPlate)
            return true
        }
        return false
    }
}
