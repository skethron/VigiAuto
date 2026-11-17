package br.com.vigiauto

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.vigiauto.data.db.AppDatabase
import br.com.vigiauto.data.db.VehicleRecordDao
import br.com.vigiauto.data.db.VehicleRecordEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VehicleRecordDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: VehicleRecordDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.vehicleRecordDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun entity(plate: String) = VehicleRecordEntity(
        normalizedPlate = plate,
        displayPlate = plate,
        firstDetectionTimestamp = 1_000L,
        lastSeenTimestamp = 1_000L
    )

    @Test
    fun placaNormalizadaEhUnica() = runBlocking {
        val firstId = dao.insertIgnore(entity("ABC1234"))
        assertTrue(firstId > 0)

        val secondId = dao.insertIgnore(entity("ABC1234"))
        assertEquals(-1L, secondId)
        assertEquals(1, dao.getAll().size)
    }

    @Test
    fun releituraAtualizaUltimaVisualizacaoEOcorrencias() = runBlocking {
        dao.insertIgnore(entity("ABC1234"))
        dao.touchSeen("ABC1234", 5_000L, 0.91f)

        val record = dao.findByPlate("ABC1234")!!
        assertEquals(5_000L, record.lastSeenTimestamp)
        assertEquals(2, record.occurrenceCount)
        assertEquals(0.91f, record.recognitionConfidence)
        assertEquals(1_000L, record.firstDetectionTimestamp)
    }

    @Test
    fun fotografiaOriginalNaoEhSubstituidaAutomaticamente() = runBlocking {
        val id = dao.insertIgnore(entity("ABC1234"))

        val firstWrite = dao.setPhotoIfAbsent(id, "primeira.jpg", 2_000L, "GOOD")
        val secondWrite = dao.setPhotoIfAbsent(id, "segunda.jpg", 3_000L, "LIMITED")

        assertEquals(1, firstWrite)
        assertEquals(0, secondWrite)
        assertEquals("primeira.jpg", dao.findByPlate("ABC1234")!!.photoPath)
    }

    @Test
    fun substituicaoManualDeFotoFunciona() = runBlocking {
        val id = dao.insertIgnore(entity("ABC1234"))
        dao.setPhotoIfAbsent(id, "primeira.jpg", 2_000L, "GOOD")
        dao.replacePhoto(id, "nova.jpg", 3_000L, "GOOD")

        assertEquals("nova.jpg", dao.findByPlate("ABC1234")!!.photoPath)

        dao.clearPhoto(id)
        val record = dao.findByPlate("ABC1234")!!
        assertNull(record.photoPath)
        assertEquals("UNAVAILABLE", record.photoQualityStatus)
    }

    @Test
    fun capturaComRecorteNaoEhSubstituidaAutomaticamente() = runBlocking {
        val id = dao.insertIgnore(entity("ABC1234"))

        val firstWrite = dao.setCaptureIfAbsent(
            id = id,
            photoPath = "vehicle_1_a.jpg",
            timestamp = 2_000L,
            quality = "GOOD",
            cropPath = "plate_1_a.jpg",
            ocrText = "ABC1D23",
            boxLeft = 0.5f,
            boxTop = 0.6f,
            boxRight = 0.7f,
            boxBottom = 0.65f,
            detectorConfidence = 0.93f,
            ocrConfidence = 0.88f,
            cropAspectRatio = 2.2f,
            plateQuality = "GOOD",
            needsReview = false
        )
        val secondWrite = dao.setCaptureIfAbsent(
            id = id,
            photoPath = "vehicle_1_b.jpg",
            timestamp = 3_000L,
            quality = "LIMITED",
            cropPath = "plate_1_b.jpg",
            ocrText = "ABC1D23",
            boxLeft = 0.5f,
            boxTop = 0.6f,
            boxRight = 0.7f,
            boxBottom = 0.65f,
            detectorConfidence = 0.60f,
            ocrConfidence = 0.61f,
            cropAspectRatio = 2.2f,
            plateQuality = "LOW",
            needsReview = true
        )

        assertEquals(1, firstWrite)
        assertEquals(0, secondWrite)
        val record = dao.findByPlate("ABC1234")!!
        assertEquals("vehicle_1_a.jpg", record.photoPath)
        assertEquals("plate_1_a.jpg", record.plateCropPath)
        assertEquals(0.5f, record.plateBoxLeft)
        assertEquals(0.93f, record.plateDetectorConfidence)
        assertEquals(0.88f, record.plateOcrConfidence)
    }

    @Test
    fun limpezaDaFotoTambemRemoveMetadadosDoRecorte() = runBlocking {
        val id = dao.insertIgnore(entity("ABC1234"))
        dao.setCaptureIfAbsent(
            id = id,
            photoPath = "vehicle_1_a.jpg",
            timestamp = 2_000L,
            quality = "GOOD",
            cropPath = "plate_1_a.jpg",
            ocrText = "ABC1D23",
            boxLeft = 0.5f,
            boxTop = 0.6f,
            boxRight = 0.7f,
            boxBottom = 0.65f,
            detectorConfidence = 0.93f,
            ocrConfidence = 0.88f,
            cropAspectRatio = 2.2f,
            plateQuality = "GOOD",
            needsReview = false
        )

        assertTrue(dao.allPhotoPaths().containsAll(listOf("vehicle_1_a.jpg", "plate_1_a.jpg")))
        dao.clearPhoto(id)

        val record = dao.findByPlate("ABC1234")!!
        assertNull(record.photoPath)
        assertNull(record.plateCropPath)
        assertNull(record.plateOcrText)
        assertNull(record.plateBoxLeft)
        assertEquals("UNAVAILABLE", record.plateQualityStatus)
        assertTrue(!record.plateNeedsReview)
    }

    @Test
    fun consultaAtualizaDadosDoVeiculo() = runBlocking {
        dao.insertIgnore(entity("ABC1234"))
        dao.applyLookup(
            plate = "ABC1234",
            status = "COMPLETED",
            timestamp = 9_000L,
            brand = "Fiat",
            model = "Cronos",
            version = "Drive",
            color = "Prata",
            manufactureYear = 2022,
            modelYear = 2023,
            category = "Automóvel",
            city = "São Paulo",
            state = "SP",
            restrictionStatus = "NONE_IDENTIFIED",
            restrictionDescription = "Nada consta",
            sourceName = "fonte de teste"
        )

        val record = dao.findByPlate("ABC1234")!!
        assertEquals("COMPLETED", record.queryStatus)
        assertEquals("Fiat", record.brand)
        assertEquals(2022, record.manufactureYear)
        assertEquals("São Paulo", record.city)
        assertEquals("SP", record.state)
        assertEquals(9_000L, record.queryTimestamp)
    }

    @Test
    fun consultaNmtPersisteResultadoSemAlterarCategoria() = runBlocking {
        val id = dao.insertIgnore(entity("ABC1234"))
        dao.applyLookup(
            plate = "ABC1234",
            status = "COMPLETED",
            timestamp = 9_000L,
            brand = "Fiat",
            model = "Cronos",
            version = "Drive",
            color = "Prata",
            manufactureYear = 2022,
            modelYear = 2023,
            category = "Automóvel",
            city = "São Paulo",
            state = "SP",
            restrictionStatus = "NONE_IDENTIFIED",
            restrictionDescription = "Nada consta",
            sourceName = "fonte de teste"
        )

        dao.updateNmtResult(
            id = id,
            status = "REGULAR",
            checkedAt = 10_000L,
            rawStatus = "REGULAR",
            attempts = 0,
            nextAttemptAt = 20_000L
        )
        val record = dao.findByPlate("ABC1234")!!
        assertEquals("REGULAR", record.nmtStatus)
        assertEquals("REGULAR", record.nmtRawStatus)
        assertEquals(10_000L, record.nmtCheckedAt)
        assertEquals(0, record.nmtAttempts)
        assertEquals(20_000L, record.nmtNextAttemptAt)
        assertEquals("Automóvel", record.vehicleCategory)
    }

    @Test
    fun filaNmtRespeitaProximaTentativa() = runBlocking {
        val id = dao.insertIgnore(entity("ABC1234"))
        dao.updateNmtResult(
            id = id,
            status = "UNAVAILABLE",
            checkedAt = 10_000L,
            rawStatus = null,
            attempts = 1,
            nextAttemptAt = 30_000L
        )

        assertNull(dao.nextNmtCandidate(now = 20_000L))
        assertEquals("ABC1234", dao.nextNmtCandidate(now = 30_000L)?.normalizedPlate)
    }

    @Test
    fun contagemDePendentesConsideraPendenteEAndamento() = runBlocking {
        dao.insertIgnore(entity("ABC1234"))
        dao.insertIgnore(entity("XYZ9876"))
        dao.insertIgnore(entity("DEF4567"))
        dao.updateQueryStatus("ABC1234", "PENDING", 0L)
        dao.updateQueryStatus("XYZ9876", "IN_PROGRESS", 0L)
        dao.updateQueryStatus("DEF4567", "COMPLETED", 0L)

        dao.observePendingCount().let { flow ->
            assertEquals(2, flow.first())
        }
    }
}
