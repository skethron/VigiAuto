package br.com.vigiauto

import br.com.vigiauto.domain.model.PlateCandidate
import br.com.vigiauto.domain.plate.OcrCandidateMerger
import br.com.vigiauto.domain.plate.PlateBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrCandidateMergerTest {

    private fun candidate(
        plate: String,
        confidence: Float = 0.75f,
        box: PlateBox = PlateBox(0f, 0f, 200f, 60f)
    ) = PlateCandidate(
        rawText = plate,
        normalized = plate,
        confidence = confidence,
        box = box
    )

    @Test
    fun `recorte corrige Q lido como O`() {
        val merged = OcrCandidateMerger.merge(
            original = listOf(candidate("ABC1O23", 0.85f)),
            refined = listOf(candidate("ABC1Q23", 0.80f))
        )
        assertEquals("ABC1Q23", merged.first().normalized)
    }

    @Test
    fun `recorte corrige C lido como G`() {
        val merged = OcrCandidateMerger.merge(
            original = listOf(candidate("ABC1C23", 0.85f)),
            refined = listOf(candidate("ABC1G23", 0.82f))
        )
        assertEquals("ABC1G23", merged.first().normalized)
    }

    @Test
    fun `leitura sem relacao e descartada`() {
        val original = listOf(candidate("ABC1O23", 0.85f))
        val merged = OcrCandidateMerger.merge(
            original = original,
            refined = listOf(candidate("XYZ4E56", 0.99f))
        )
        assertEquals("ABC1O23", merged.first().normalized)
        assertEquals(original.first().confidence, merged.first().confidence, 0.0001f)
    }

    @Test
    fun `leitura refinada ganha prioridade no consenso`() {
        val merged = OcrCandidateMerger.merge(
            original = listOf(candidate("ABC1O23", 0.70f)),
            refined = listOf(candidate("ABC1Q23", 0.65f))
        )
        assertTrue(merged.first().confidence > 0.70f)
    }

    @Test
    fun `recorte mantem a caixa do quadro completo do candidato ancora`() {
        val fullFrameBox = PlateBox(410f, 300f, 610f, 360f)
        val merged = OcrCandidateMerger.merge(
            original = listOf(candidate("ABC1O23", 0.85f, fullFrameBox)),
            // O OCR do recorte devolve coordenadas relativas ao recorte, que
            // nao podem ser usadas para fotografar o veiculo.
            refined = listOf(candidate("ABC1Q23", 0.80f, PlateBox(2f, 4f, 198f, 63f)))
        )

        assertEquals("ABC1Q23", merged.first().normalized)
        assertEquals(fullFrameBox, merged.first().box)
    }
}
