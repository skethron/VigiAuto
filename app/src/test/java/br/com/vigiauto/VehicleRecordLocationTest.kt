package br.com.vigiauto

import br.com.vigiauto.domain.model.VehicleRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleRecordLocationTest {

    @Test
    fun `localizacao completa fica em um paragrafo com virgulas`() {
        val record = VehicleRecord(
            id = 1L,
            normalizedPlate = "ABC1D23",
            displayPlate = "ABC1D23",
            locationStreet = "Estrada Nimbus",
            locationNumber = "97",
            locationNeighborhood = "Chácara Vitapolis",
            locationCity = "Itapevi",
            locationState = "SP",
            locationPostalCode = "06600-000",
            firstDetectionTimestamp = 0L,
            lastSeenTimestamp = 0L
        )

        assertEquals(
            "Estrada Nimbus, 97, Chácara Vitapolis, Itapevi/SP, CEP 06600-000",
            record.locationFullLine
        )
    }

    @Test
    fun `localizacao ausente continua nula`() {
        val record = VehicleRecord(
            id = 1L,
            normalizedPlate = "ABC1D23",
            displayPlate = "ABC1D23",
            firstDetectionTimestamp = 0L,
            lastSeenTimestamp = 0L
        )

        assertNull(record.locationFullLine)
    }
}
