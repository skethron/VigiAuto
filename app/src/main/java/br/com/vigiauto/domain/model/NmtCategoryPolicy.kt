package br.com.vigiauto.domain.model

/**
 * Decide se uma nova consulta NMT pode substituir a categoria já persistida.
 *
 * Uma falha de rede, timeout ou reCAPTCHA não é uma nova informação sobre o
 * veículo. Se já existe uma categoria concluída, ela deve permanecer visível
 * enquanto o worker tenta novamente em segundo plano. Sem essa regra, uma
 * falha temporária apagava a categoria que o usuário já tinha visto.
 */
object NmtCategoryPolicy {

    fun shouldPreserve(previousStatus: String?, incoming: NmtStatus): Boolean {
        if (incoming.isResolved) return false
        val previous = previousStatus
            ?.let { runCatching { NmtStatus.valueOf(it) }.getOrNull() }
            ?: return false
        return previous.isResolved
    }
}
