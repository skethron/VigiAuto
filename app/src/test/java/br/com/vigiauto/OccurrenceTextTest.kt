package br.com.vigiauto

import br.com.vigiauto.domain.model.formatOccurrenceDate
import br.com.vigiauto.domain.model.normalizeOccurrenceDates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OccurrenceTextTest {

    @Test
    fun `formata data ISO para dia mes ano`() {
        assertEquals("02-01-2025", formatOccurrenceDate("2025-01-02"))
        assertEquals("02-01-2025", formatOccurrenceDate("2025-01-02T00:00:00"))
        assertNull(formatOccurrenceDate(null))
        assertNull(formatOccurrenceDate("  "))
    }

    @Test
    fun `normaliza texto antigo gravado no banco`() {
        assertEquals(
            "Veículo roubado/furtado - SENATRAN (Ocorrência: 02-01-2025)",
            normalizeOccurrenceDates(
                "Veículo roubado/furtado - SENATRAN (ocorrência: 2025-01-02T00:00:00)"
            )
        )
        assertEquals(
            "Veículo roubado/furtado (Ocorrência: 02-01-2025)",
            normalizeOccurrenceDates("Veículo roubado/furtado (ocorrência: 2025-1-2)")
        )
        assertEquals("Sem ocorrência", normalizeOccurrenceDates("Sem ocorrência"))
        assertNull(normalizeOccurrenceDates(null))
    }
}
