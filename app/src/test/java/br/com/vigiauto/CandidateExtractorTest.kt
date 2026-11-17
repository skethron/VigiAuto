package br.com.vigiauto

import br.com.vigiauto.domain.plate.CandidateExtractor
import br.com.vigiauto.domain.plate.OcrDocument
import br.com.vigiauto.domain.plate.OcrElement
import br.com.vigiauto.domain.plate.OcrLine
import br.com.vigiauto.domain.plate.PlateBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateExtractorTest {

    private fun line(text: String, top: Float, bottom: Float, left: Float = 0f, right: Float = 400f) =
        OcrLine(text = text, box = PlateBox(left, top, right, bottom), elements = emptyList())

    @Test
    fun `extrai placa completa em uma unica linha`() {
        val document = OcrDocument(
            lines = listOf(line("DEI3A56", 100f, 200f))
        )
        val candidates = CandidateExtractor.extract(document)
        val candidate = candidates.firstOrNull { it.normalized == "DEI3A56" }
        assertTrue(candidate != null)
        assertTrue(candidate!!.confidence >= 0.8f)
        assertEquals(1, candidate.lineCount)
    }

    @Test
    fun `extrai placa antiga com hifen`() {
        val document = OcrDocument(lines = listOf(line("EFR-8092", 50f, 120f)))
        assertTrue(CandidateExtractor.extract(document).any { it.normalized == "EFR8092" })
    }

    @Test
    fun `junta elementos fragmentados da mesma linha`() {
        val elements = listOf(
            OcrElement("SHQ", PlateBox(0f, 0f, 120f, 60f)),
            OcrElement("9G69", PlateBox(124f, 0f, 260f, 60f))
        )
        val document = OcrDocument(
            lines = listOf(OcrLine("SHQ 9G69", PlateBox(0f, 0f, 260f, 60f), elements))
        )
        val candidates = CandidateExtractor.extract(document)
        assertTrue(candidates.any { it.normalized == "SHQ9G69" })
    }

    @Test
    fun `junta duas linhas de placa de motocicleta`() {
        val document = OcrDocument(
            lines = listOf(
                line("SUK", 100f, 180f),
                line("6E51", 190f, 270f)
            )
        )
        val candidate = CandidateExtractor.extract(document)
            .firstOrNull { it.normalized == "SUK6E51" }
        assertTrue(candidate != null)
        assertEquals(2, candidate!!.lineCount)
    }

    @Test
    fun `nao junta linhas distantes verticalmente`() {
        val document = OcrDocument(
            lines = listOf(
                line("SUK", 0f, 60f),
                line("6E51", 400f, 460f)
            )
        )
        assertTrue(CandidateExtractor.extract(document).none { it.normalized == "SUK6E51" })
    }

    @Test
    fun `ignora textos de cabecalho da placa`() {
        val document = OcrDocument(
            lines = listOf(
                line("BRASIL", 0f, 40f),
                line("MERCOSUL", 45f, 80f)
            )
        )
        assertTrue(CandidateExtractor.extract(document).isEmpty())
    }

    @Test
    fun `placa exata tem confianca maior que placa corrigida`() {
        val exact = CandidateExtractor.extract(
            OcrDocument(lines = listOf(line("ABC1234", 0f, 100f)))
        ).first { it.normalized == "ABC1234" }

        val corrected = CandidateExtractor.extract(
            OcrDocument(lines = listOf(line("ABCI234", 0f, 100f)))
        ).first { it.normalized == "ABC1234" }

        assertTrue(exact.confidence > corrected.confidence)
    }
}
