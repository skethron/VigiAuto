package br.com.vigiauto.domain.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val occurrenceDateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

/**
 * Converte a data ISO devolvida pela fonte para o formato exibido no app.
 * Se a fonte enviar um formato desconhecido, o texto original é preservado.
 */
fun formatOccurrenceDate(value: String?): String? {
    val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val datePart = trimmed.take(10)
    return runCatching {
        LocalDate.parse(datePart).format(occurrenceDateFormatter)
    }.getOrDefault(trimmed)
}

private val occurrencePattern = Regex(
    """(?i)\(\s*ocorr[êÊeE]ncia\s*:\s*(\d{4})-(\d{1,2})-(\d{1,2})(?:[T\s][^)]*)?\s*\)"""
)

/**
 * Normaliza também os textos já gravados no banco, que podem conter a data
 * ISO completa, como "(ocorrência: 2025-01-02T00:00:00)".
 */
fun normalizeOccurrenceDates(value: String?): String? = value?.replace(occurrencePattern) { match ->
    val year = match.groupValues[1].toIntOrNull()
    val month = match.groupValues[2].toIntOrNull()
    val day = match.groupValues[3].toIntOrNull()
    val formatted = if (year != null && month != null && day != null) {
        runCatching {
            LocalDate.of(year, month, day).format(occurrenceDateFormatter)
        }.getOrNull()
    } else {
        null
    }
    if (formatted == null) match.value else "(Ocorrência: $formatted)"
}
