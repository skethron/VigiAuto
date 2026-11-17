package br.com.vigiauto

import br.com.vigiauto.domain.plate.PlateNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateNormalizerTest {

    @Test
    fun `placa antiga valida e retornada como esta`() {
        assertEquals("ABC1234", PlateNormalizer.normalize("ABC1234"))
        assertEquals("DET8075", PlateNormalizer.normalize("DET 8075"))
        assertEquals("EFR8092", PlateNormalizer.normalize("EFR-8092"))
    }

    @Test
    fun `placa mercosul valida e retornada como esta`() {
        assertEquals("DEI3A56", PlateNormalizer.normalize("DEI3A56"))
        assertEquals("SHQ9G69", PlateNormalizer.normalize("SHQ9G69"))
        assertEquals("PVL7H74", PlateNormalizer.normalize("PVL7H74"))
    }

    @Test
    fun `normaliza minusculas e separadores`() {
        assertEquals("ABC1234", PlateNormalizer.normalize("abc-1234"))
        assertEquals("SUK6E51", PlateNormalizer.normalize("suk 6e51"))
    }

    @Test
    fun `corrige confusoes de caracteres por posicao`() {
        // I no lugar de 1 na posicao de digito
        assertEquals("ABC1234", PlateNormalizer.normalize("ABCI234"))
        // O no lugar de 0 na ultima posicao (digito)
        assertEquals("ABC1230", PlateNormalizer.normalize("ABC123O"))
        // S/B no lugar de 5/8 na ultima posicao
        assertEquals("ABC1235", PlateNormalizer.normalize("ABC123S"))
        assertEquals("ABC1238", PlateNormalizer.normalize("ABC123B"))
    }

    @Test
    fun `nao altera letra valida em posicao de letra do mercosul`() {
        // "O" é letra legítima na 5ª posição do padrão Mercosul
        assertEquals("ABC1O23", PlateNormalizer.normalize("ABC1O23"))
    }

    @Test
    fun `gera alternativas para ambiguidades entre antigo e mercosul`() {
        assertTrue(PlateNormalizer.alternatives("ABC1123").contains("ABC1I23"))
        assertTrue(PlateNormalizer.alternatives("ABC1I23").contains("ABC1123"))
        assertTrue(PlateNormalizer.alternatives("ABC1023").contains("ABC1O23"))
        assertTrue(PlateNormalizer.alternatives("ABC1O23").contains("ABC1023"))
    }

    @Test
    fun `variante O zero da quinta posicao e a primeira consultada`() {
        assertEquals("ABC1O23", PlateNormalizer.alternatives("ABC1023").first())
        assertEquals("ABC1023", PlateNormalizer.alternatives("ABC1O23").first())
        assertEquals("ABC1O23", PlateNormalizer.fifthOZeroVariant("ABC1023"))
        assertEquals("ABC1023", PlateNormalizer.fifthOZeroVariant("ABC1O23"))
        // A troca só existe quando a quinta posição é O/0.
        assertNull(PlateNormalizer.fifthOZeroVariant("ABC1124"))
        // Aqui a troca forma uma Mercosul válida, então é devolvida.
        assertEquals("ABC1O24", PlateNormalizer.fifthOZeroVariant("ABC1024"))
    }

    @Test
    fun `regra O zero segue as posicoes de cada padrao`() {
        // Mercosul: letras nas posicoes 1, 2, 3 e 5; digitos nas 4, 6 e 7.
        assertEquals("ABO1D23", PlateNormalizer.normalize("AB01D23"))
        assertEquals("ABC1D20", PlateNormalizer.normalize("ABC1D2O"))
        assertEquals("OBC1D23", PlateNormalizer.normalize("0BC1D23"))
        assertEquals("ABC1D23", PlateNormalizer.normalize("ABC1D23"))

        // Antiga: letras nas posicoes 1, 2 e 3; digitos nas 4, 5, 6 e 7.
        assertEquals("ABC1200", PlateNormalizer.normalize("ABC12OO"))
        assertEquals("ABC1023", PlateNormalizer.normalize("ABC1023"))

        // A quinta posicao e a unica em que os dois padroes se sobrepoem:
        // "ABC1O23" e Mercosul valida e "ABC1023" e antiga valida. A leitura
        // e preservada e a outra hipotese vai para as alternativas.
        assertEquals("ABC1O23", PlateNormalizer.normalize("ABC1O23"))
        assertEquals("ABC1023", PlateNormalizer.alternatives("ABC1O23").first())
    }

    @Test
    fun `gera alternativa para confusao entre U e V`() {
        assertTrue(PlateNormalizer.alternatives("ABU1234").contains("ABV1234"))
        assertTrue(PlateNormalizer.alternatives("ABV1234").contains("ABU1234"))
        assertTrue(PlateNormalizer.alternatives("ABU1C23").contains("ABV1C23"))
        assertTrue(PlateNormalizer.alternatives("ABU1V23").contains("ABV1U23"))
    }

    @Test
    fun `gera alternativa para Q lido como O`() {
        assertTrue(PlateNormalizer.alternatives("ABC1O23").contains("ABC1Q23"))
        assertTrue(PlateNormalizer.alternatives("ABQ1234").contains("ABO1234"))
    }

    @Test
    fun `gera alternativa para C lido como G ou O`() {
        assertTrue(PlateNormalizer.alternatives("ABC1C23").contains("ABC1G23"))
        assertTrue(PlateNormalizer.alternatives("ABG1234").contains("ABC1234"))
        assertTrue(PlateNormalizer.alternatives("ABC1O23").contains("ABC1C23"))
    }

    @Test
    fun `prioriza trocas simples antes de duas trocas`() {
        val alternatives = PlateNormalizer.alternatives("ABO1C23")
        assertTrue(alternatives.isNotEmpty())
        assertEquals(7, alternatives.first().length)
        assertTrue(alternatives.take(2).all { it.count { char -> char != 'O' } >= 6 })
    }

    @Test
    fun `reconhece equivalencia visual entre letras confundidas`() {
        assertTrue(PlateNormalizer.isVisuallyEquivalent("ABC1O23", "ABC1Q23"))
        assertTrue(PlateNormalizer.isVisuallyEquivalent("ABC1C23", "ABC1G23"))
        assertTrue(PlateNormalizer.isVisuallyEquivalent("ABC1C23", "ABC1O23"))
        assertTrue(!PlateNormalizer.isVisuallyEquivalent("ABC1O23", "ABC1D23"))
        assertTrue(!PlateNormalizer.isVisuallyEquivalent("ABC1O23", "XYZ1O23"))
    }

    @Test
    fun `nao gera alternativas para placa sem confusao visual`() {
        assertTrue(PlateNormalizer.alternatives("ABF2345").isEmpty())
    }

    @Test
    fun `rejeita textos que exigem muitas correcoes`() {
        assertNull(PlateNormalizer.normalize("1234567"))
    }

    @Test
    fun `rejeita comprimentos diferentes de sete`() {
        assertNull(PlateNormalizer.normalize("ABC123"))
        assertNull(PlateNormalizer.normalize("ABC12345"))
        assertNull(PlateNormalizer.normalize(""))
    }

    @Test
    fun `rejeita palavras comuns do painel e da placa`() {
        assertNull(PlateNormalizer.normalize("BRASIL"))
        assertNull(PlateNormalizer.normalize("MERCOSUL"))
        assertNull(PlateNormalizer.normalize("SP SAO PAULO"))
    }
}
