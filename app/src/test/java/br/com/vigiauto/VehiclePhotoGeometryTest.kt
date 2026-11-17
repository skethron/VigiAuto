package br.com.vigiauto

import br.com.vigiauto.data.photos.PixelRect
import br.com.vigiauto.data.photos.VehiclePhotoGeometry
import br.com.vigiauto.domain.plate.PlateBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehiclePhotoGeometryTest {

    @Test
    fun quadradoFicaCentralizadoNoVeiculoEAcimaDaPlaca() {
        val box = PlateBox(500f, 600f, 700f, 640f)
        val square = VehiclePhotoGeometry.squareAround(box, 1280, 720)!!

        assertEquals(720, square.width)
        assertEquals(720, square.height)
        assertTrue(square.left in 0..(1280 - square.width))
        assertTrue(square.top in 0..(720 - square.height))
        assertEquals(720, square.right - square.left)
    }

    @Test
    fun quadradoRespeitaBordasDoQuadro() {
        val leftEdge = VehiclePhotoGeometry.squareAround(
            PlateBox(2f, 590f, 120f, 620f),
            1280,
            720
        )!!
        val rightEdge = VehiclePhotoGeometry.squareAround(
            PlateBox(1160f, 590f, 1278f, 620f),
            1280,
            720
        )!!

        assertEquals(0, leftEdge.left)
        assertEquals(1280, rightEdge.right)
        assertTrue(leftEdge.top >= 0)
        assertTrue(rightEdge.top >= 0)
    }

    @Test
    fun recorteUsaExatamenteACaixaDaPlacaComArredondamentoMinimo() {
        val rect = VehiclePhotoGeometry.plateRect(
            PlateBox(100.2f, 200.1f, 320.8f, 240.9f),
            1280,
            720
        )!!

        assertEquals(100, rect.left)
        assertEquals(200, rect.top)
        assertEquals(321, rect.right)
        assertEquals(241, rect.bottom)
    }

    @Test
    fun caixaInvalidaNaoGeraRecorte() {
        assertNull(VehiclePhotoGeometry.plateRect(PlateBox(10f, 10f, 12f, 11f), 1280, 720))
        assertNull(VehiclePhotoGeometry.plateRect(PlateBox.EMPTY, 1280, 720))
    }

    @Test
    fun normalizacaoMantemCoordenadasDentroDoQuadro() {
        assertEquals(0.5f, VehiclePhotoGeometry.normalize(640f, 1280)!!, 0.0001f)
        assertEquals(0f, VehiclePhotoGeometry.normalize(-10f, 1280)!!, 0.0001f)
        assertEquals(1f, VehiclePhotoGeometry.normalize(2000f, 1280)!!, 0.0001f)
        assertNull(VehiclePhotoGeometry.normalize(10f, 0))
    }

    @Test
    fun miniaturaUsaACaixaDaPlacaComMargemDentroDaFoto() {
        val square = VehiclePhotoGeometry.squareAround(
            PlateBox(500f, 600f, 700f, 640f),
            1280,
            720
        )!!
        val thumb = VehiclePhotoGeometry.plateThumbRect(
            box = PlateBox(500f, 600f, 700f, 640f),
            square = square,
            photoWidth = square.width,
            photoHeight = square.height
        )!!

        // A caixa da placa, relativa ao quadrado, fica dentro da miniatura.
        val boxLeftInPhoto = 500 - square.left
        val boxTopInPhoto = 600 - square.top
        val boxRightInPhoto = 700 - square.left
        val boxBottomInPhoto = 640 - square.top
        assertTrue(thumb.left < boxLeftInPhoto)
        assertTrue(thumb.top < boxTopInPhoto)
        assertTrue(thumb.right > boxRightInPhoto)
        assertTrue(thumb.bottom > boxBottomInPhoto)
        assertTrue(thumb.left >= 0 && thumb.top >= 0)
        assertTrue(thumb.right <= square.width && thumb.bottom <= square.height)
    }

    @Test
    fun miniaturaAcompanhaAReducaoDaFoto() {
        val square = PixelRect(200, 0, 920, 720)
        val full = VehiclePhotoGeometry.plateThumbRect(
            box = PlateBox(500f, 600f, 700f, 640f),
            square = square,
            photoWidth = 720,
            photoHeight = 720
        )!!
        val half = VehiclePhotoGeometry.plateThumbRect(
            box = PlateBox(500f, 600f, 700f, 640f),
            square = square,
            photoWidth = 360,
            photoHeight = 360
        )!!

        assertEquals(full.left / 2, half.left)
        assertEquals(full.top / 2, half.top)
        assertEquals(full.right / 2, half.right)
        assertEquals(full.bottom / 2, half.bottom)
    }

    @Test
    fun miniaturaRecusaCaixaDeOutroEnquadramento() {
        val square = PixelRect(0, 0, 720, 720)
        assertNull(
            VehiclePhotoGeometry.plateThumbRect(
                box = PlateBox(800f, 800f, 980f, 860f),
                square = square,
                photoWidth = 720,
                photoHeight = 720
            )
        )
    }
}
