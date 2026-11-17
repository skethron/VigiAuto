package br.com.vigiauto

import br.com.vigiauto.domain.model.QueryStatus
import br.com.vigiauto.domain.model.RestrictionStatus
import br.com.vigiauto.ui.components.NO_THEFT_ALERT_MESSAGE
import br.com.vigiauto.ui.components.isSinespOrSimpleSource
import br.com.vigiauto.ui.components.noTheftAlertMessageOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestrictionDisplayTest {

    @Test
    fun `identifica fontes sinesp e consulta simples`() {
        assertTrue(isSinespOrSimpleSource("SINESP Cidadão (v1)"))
        assertTrue(isSinespOrSimpleSource("Consulta Simples (API)"))
        assertTrue(isSinespOrSimpleSource("Consulta Placa (API)"))
        assertFalse(isSinespOrSimpleSource("Consulta Placa (HTTP provisório)"))
        assertFalse(isSinespOrSimpleSource("DEMO — dados fictícios"))
        assertFalse(isSinespOrSimpleSource(null))
        assertFalse(isSinespOrSimpleSource(""))
    }

    @Test
    fun `mostra mensagem amigavel quando nao ha roubo ou furto`() {
        assertEquals(
            NO_THEFT_ALERT_MESSAGE,
            noTheftAlertMessageOrNull(
                queryStatus = QueryStatus.COMPLETED,
                restrictionStatus = RestrictionStatus.INCONCLUSIVE,
                restrictionDescription = "A fonte não informou a situação de roubo/furto.",
                sourceName = "SINESP Cidadão (v1)"
            )
        )
        assertEquals(
            NO_THEFT_ALERT_MESSAGE,
            noTheftAlertMessageOrNull(
                queryStatus = QueryStatus.INCOMPLETE,
                restrictionStatus = RestrictionStatus.UNKNOWN,
                restrictionDescription = null,
                sourceName = "Consulta Simples (API)"
            )
        )
        assertEquals(
            NO_THEFT_ALERT_MESSAGE,
            noTheftAlertMessageOrNull(
                queryStatus = QueryStatus.COMPLETED,
                restrictionStatus = RestrictionStatus.NONE_IDENTIFIED,
                restrictionDescription = "Sem registro ou alerta de roubo/furto - SENATRAN",
                sourceName = "SINESP Cidadão (v1)"
            )
        )
    }

    @Test
    fun `mantem inconclusivo quando a fonte nao permitiu concluir`() {
        assertNull(
            noTheftAlertMessageOrNull(
                queryStatus = QueryStatus.COMPLETED,
                restrictionStatus = RestrictionStatus.CONFIRMED,
                restrictionDescription = "Veículo com registro de roubo/furto.",
                sourceName = "SINESP Cidadão (v1)"
            )
        )
        assertNull(
            noTheftAlertMessageOrNull(
                queryStatus = QueryStatus.COMPLETED,
                restrictionStatus = RestrictionStatus.INCONCLUSIVE,
                restrictionDescription = "A situação de roubo/furto não foi reconhecida.",
                sourceName = "SINESP Cidadão (v1)"
            )
        )
        assertNull(
            noTheftAlertMessageOrNull(
                queryStatus = QueryStatus.COMPLETED,
                restrictionStatus = RestrictionStatus.INCONCLUSIVE,
                restrictionDescription = "Histórico indisponível.",
                sourceName = "Consulta Simples (API)"
            )
        )
        assertNull(
            noTheftAlertMessageOrNull(
                queryStatus = QueryStatus.NOT_FOUND,
                restrictionStatus = RestrictionStatus.UNKNOWN,
                restrictionDescription = null,
                sourceName = "SINESP Cidadão (v1)"
            )
        )
        assertNull(
            noTheftAlertMessageOrNull(
                queryStatus = QueryStatus.CONNECTION_FAILURE,
                restrictionStatus = RestrictionStatus.UNKNOWN,
                restrictionDescription = null,
                sourceName = "SINESP Cidadão (v1)"
            )
        )
        assertNull(
            noTheftAlertMessageOrNull(
                queryStatus = QueryStatus.COMPLETED,
                restrictionStatus = RestrictionStatus.INCONCLUSIVE,
                restrictionDescription = "A fonte não informou a situação de roubo/furto.",
                sourceName = "Consulta Placa (HTTP provisório)"
            )
        )
    }
}
