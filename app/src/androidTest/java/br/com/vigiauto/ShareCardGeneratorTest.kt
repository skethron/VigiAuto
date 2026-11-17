package br.com.vigiauto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.vigiauto.domain.model.QueryStatus
import br.com.vigiauto.domain.model.RestrictionStatus
import br.com.vigiauto.domain.model.VehicleRecord
import br.com.vigiauto.ui.details.ShareCardGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class ShareCardGeneratorTest {

    @Test
    fun geraCardCompletoComPlacaCentralizadaSemStatus() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceFile = File(target.cacheDir, "share-card-source.jpg")
        val source = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        Canvas(source).drawColor(Color.rgb(28, 72, 120))
        FileOutputStream(sourceFile).use { output ->
            source.compress(Bitmap.CompressFormat.JPEG, 90, output)
        }
        source.recycle()

        val cropFile = File(target.cacheDir, "share-card-plate.jpg")
        val crop = Bitmap.createBitmap(320, 120, Bitmap.Config.ARGB_8888)
        Canvas(crop).drawColor(Color.rgb(210, 220, 230))
        FileOutputStream(cropFile).use { output ->
            crop.compress(Bitmap.CompressFormat.JPEG, 93, output)
        }
        crop.recycle()

        val now = System.currentTimeMillis()
        val record = VehicleRecord(
            id = 1L,
            normalizedPlate = "ABC1D23",
            displayPlate = "ABC1D23",
            brand = "HONDA",
            model = "CG150 FAN ESDI",
            color = "VERMELHA",
            manufactureYear = 2014,
            vehicleCategory = "Veículo particular",
            locationStreet = "Estrada Nimbus",
            locationNumber = "97",
            locationNeighborhood = "Chácara Vitapolis",
            locationCity = "Itapevi",
            locationState = "SP",
            locationPostalCode = "06600-000",
            restrictionStatus = RestrictionStatus.CONFIRMED,
            restrictionDescription = "Veículo roubado/furtado - SENATRAN (ocorrência: 2019-09-24T00:00:00)",
            sourceName = "SINESP Cidadão (v1)",
            firstDetectionTimestamp = now - 60_000L,
            lastSeenTimestamp = now,
            queryTimestamp = now,
            queryStatus = QueryStatus.COMPLETED,
            recognitionConfidence = 0.95f,
            occurrenceCount = 1
        )

        val uri = ShareCardGenerator(target).generate(record, sourceFile, cropFile)
        assertNotNull("O JPEG de compartilhamento não foi gerado.", uri)
        val generated = target.contentResolver.openInputStream(uri!!).use {
            BitmapFactory.decodeStream(it)
        }
        assertNotNull("O JPEG gerado não pôde ser lido.", generated)
        assertEquals(1080, generated!!.width)
        assertTrue("O card precisa ter espaço para todos os dados.", generated.height >= 1150)
        generated.recycle()
        sourceFile.delete()
        cropFile.delete()
    }
}
