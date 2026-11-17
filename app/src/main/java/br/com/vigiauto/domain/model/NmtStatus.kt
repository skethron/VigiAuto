package br.com.vigiauto.domain.model

/**
 * Resultado da consulta pública OTTC/NMT da Prefeitura de São Paulo.
 *
 * Os estados [INVALID_PLATE], [UNAVAILABLE] e [UNKNOWN] são internos: não
 * viram categoria e não têm qualquer representação visual. Eles servem para
 * controlar cache, retentativa e observabilidade do worker.
 */
enum class NmtStatus {
    REGULAR,
    IRREGULAR,
    NOT_REGISTERED,
    INVALID_PLATE,
    UNAVAILABLE,
    UNKNOWN;

    /**
     * Categoria exibida na seção de identificação do veículo. Somente os
     * estados concluídos possuem rótulo; falhas internas não alteram a
     * categoria já existente.
     */
    fun categoryLabel(): String? = when (this) {
        REGULAR -> "Veículo de aplicativo - Situação regular"
        IRREGULAR -> "Veículo de aplicativo - Situação irregular"
        NOT_REGISTERED -> "Veículo particular"
        INVALID_PLATE, UNAVAILABLE, UNKNOWN -> null
    }

    val isResolved: Boolean
        get() = this == REGULAR || this == IRREGULAR || this == NOT_REGISTERED
}
