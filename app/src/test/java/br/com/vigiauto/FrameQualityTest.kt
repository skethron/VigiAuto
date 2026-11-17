package br.com.vigiauto

import br.com.vigiauto.data.photos.FrameQuality
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameQualityTest {

    @Test
    fun `quadro com contraste tem nitidez maior que quadro uniforme`() {
        val width = 32
        val height = 24
        val flat = ByteArray(width * height) { 90.toByte() }
        val checker = ByteArray(width * height) { index ->
            val x = index % width
            val y = index / width
            if ((x / 4 + y / 4) % 2 == 0) 20.toByte() else 220.toByte()
        }

        val flatScore = FrameQuality.sharpness(flat, width, height)
        val checkerScore = FrameQuality.sharpness(checker, width, height)

        assertTrue("uniforme=$flatScore contrastado=$checkerScore", checkerScore > flatScore)
        assertTrue(checkerScore > 100f)
    }

    @Test
    fun `buffer incompativel retorna zero`() {
        assertTrue(FrameQuality.sharpness(ByteArray(4), 32, 24) == 0f)
    }
}
