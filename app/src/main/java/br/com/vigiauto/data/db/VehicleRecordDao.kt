package br.com.vigiauto.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleRecordDao {

    @Query("SELECT * FROM vehicle_records ORDER BY lastSeenTimestamp DESC")
    fun observeAll(): Flow<List<VehicleRecordEntity>>

    @Query("SELECT * FROM vehicle_records WHERE id = :id")
    fun observeById(id: Long): Flow<VehicleRecordEntity?>

    @Query("SELECT * FROM vehicle_records WHERE normalizedPlate = :plate")
    fun observeByPlate(plate: String): Flow<VehicleRecordEntity?>

    @Query("SELECT * FROM vehicle_records WHERE normalizedPlate = :plate LIMIT 1")
    suspend fun findByPlate(plate: String): VehicleRecordEntity?

    @Query("SELECT * FROM vehicle_records WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): VehicleRecordEntity?

    @Query("SELECT * FROM vehicle_records ORDER BY lastSeenTimestamp DESC")
    suspend fun getAll(): List<VehicleRecordEntity>

    @Query("SELECT COUNT(*) FROM vehicle_records")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM vehicle_records WHERE queryStatus IN ('PENDING', 'IN_PROGRESS')")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM vehicle_records WHERE queryStatus = :status ORDER BY lastSeenTimestamp ASC")
    suspend fun byStatus(status: String): List<VehicleRecordEntity>

    @Query("SELECT * FROM vehicle_records WHERE lastSeenTimestamp < :threshold ORDER BY lastSeenTimestamp ASC")
    suspend fun olderThan(threshold: Long): List<VehicleRecordEntity>

    @Query("SELECT * FROM vehicle_records ORDER BY lastSeenTimestamp ASC LIMIT :limit")
    suspend fun oldest(limit: Int): List<VehicleRecordEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: VehicleRecordEntity): Long

    @Query(
        "UPDATE vehicle_records SET lastSeenTimestamp = :timestamp, " +
            "occurrenceCount = occurrenceCount + 1, " +
            "recognitionConfidence = CASE WHEN recognitionConfidence < :confidence " +
            "THEN :confidence ELSE recognitionConfidence END " +
            "WHERE normalizedPlate = :plate"
    )
    suspend fun touchSeen(plate: String, timestamp: Long, confidence: Float)

    @Query(
        "UPDATE vehicle_records SET photoPath = :path, photoCaptureTimestamp = :timestamp, " +
            "photoQualityStatus = :quality WHERE id = :id AND photoPath IS NULL"
    )
    suspend fun setPhotoIfAbsent(id: Long, path: String, timestamp: Long, quality: String): Int

    /**
     * Grava a fotografia e o recorte no mesmo UPDATE, somente se o registro
     * ainda não tiver fotografia. Arquivos salvos antes desta chamada devem ser
     * apagados pelo repositório se o retorno for zero.
     */
    @Query(
        "UPDATE vehicle_records SET photoPath = :photoPath, " +
            "photoCaptureTimestamp = :timestamp, photoQualityStatus = :quality, " +
            "plateCropPath = :cropPath, plateOcrText = :ocrText, " +
            "plateBoxLeft = :boxLeft, plateBoxTop = :boxTop, " +
            "plateBoxRight = :boxRight, plateBoxBottom = :boxBottom, " +
            "plateDetectorConfidence = :detectorConfidence, " +
            "plateOcrConfidence = :ocrConfidence, " +
            "plateCropAspectRatio = :cropAspectRatio, " +
            "plateQualityStatus = :plateQuality, plateNeedsReview = :needsReview " +
            "WHERE id = :id AND photoPath IS NULL"
    )
    suspend fun setCaptureIfAbsent(
        id: Long,
        photoPath: String,
        timestamp: Long,
        quality: String,
        cropPath: String?,
        ocrText: String?,
        boxLeft: Float?,
        boxTop: Float?,
        boxRight: Float?,
        boxBottom: Float?,
        detectorConfidence: Float?,
        ocrConfidence: Float?,
        cropAspectRatio: Float?,
        plateQuality: String,
        needsReview: Boolean
    ): Int

    @Query(
        "UPDATE vehicle_records SET photoPath = :path, photoCaptureTimestamp = :timestamp, " +
            "photoQualityStatus = :quality, plateCropPath = NULL, plateOcrText = NULL, " +
            "plateBoxLeft = NULL, plateBoxTop = NULL, plateBoxRight = NULL, " +
            "plateBoxBottom = NULL, plateDetectorConfidence = NULL, plateOcrConfidence = NULL, " +
            "plateCropAspectRatio = NULL, plateQualityStatus = 'UNAVAILABLE', " +
            "plateNeedsReview = 0 WHERE id = :id"
    )
    suspend fun replacePhoto(id: Long, path: String, timestamp: Long, quality: String)

    @Query(
        "UPDATE vehicle_records SET photoPath = NULL, photoCaptureTimestamp = NULL, " +
            "photoQualityStatus = 'UNAVAILABLE', plateCropPath = NULL, plateOcrText = NULL, " +
            "plateBoxLeft = NULL, plateBoxTop = NULL, plateBoxRight = NULL, " +
            "plateBoxBottom = NULL, plateDetectorConfidence = NULL, plateOcrConfidence = NULL, " +
            "plateCropAspectRatio = NULL, plateQualityStatus = 'UNAVAILABLE', " +
            "plateNeedsReview = 0 WHERE id = :id"
    )
    suspend fun clearPhoto(id: Long)

    @Query("UPDATE vehicle_records SET queryStatus = :status, queryTimestamp = :timestamp WHERE normalizedPlate = :plate")
    suspend fun updateQueryStatus(plate: String, status: String, timestamp: Long)

    @Query(
        "UPDATE vehicle_records SET locationStreet = :street, locationNumber = :number, " +
            "locationNeighborhood = :neighborhood, locationCity = :city, locationState = :state, " +
            "locationPostalCode = :postalCode, locationCapturedAt = :capturedAt WHERE id = :id"
    )
    suspend fun updateLocation(
        id: Long,
        street: String?,
        number: String?,
        neighborhood: String?,
        city: String?,
        state: String?,
        postalCode: String?,
        capturedAt: Long
    )

    /**
     * Corrige a chave de uma leitura ambígua depois que a consulta confirmou
     * outra placa. O índice UNIQUE pode impedir a troca; nesse caso a linha
     * existente é preservada e o fallback não é aplicado.
     */
    @Query(
        "UPDATE OR IGNORE vehicle_records SET normalizedPlate = :newPlate, " +
            "displayPlate = :newDisplay, nmtStatus = NULL, nmtCheckedAt = NULL, " +
            "nmtRawStatus = NULL, nmtAttempts = 0, nmtNextAttemptAt = 0 WHERE id = :id"
    )
    suspend fun renamePlate(id: Long, newPlate: String, newDisplay: String): Int

    @Query(
        "UPDATE vehicle_records SET queryStatus = :status, queryTimestamp = :timestamp, " +
            "brand = :brand, model = :model, version = :version, color = :color, " +
            "manufactureYear = :manufactureYear, modelYear = :modelYear, vehicleCategory = :category, " +
            "city = :city, state = :state, " +
            "restrictionStatus = :restrictionStatus, restrictionDescription = :restrictionDescription, " +
            "sourceName = :sourceName WHERE normalizedPlate = :plate"
    )
    suspend fun applyLookup(
        plate: String,
        status: String,
        timestamp: Long,
        brand: String?,
        model: String?,
        version: String?,
        color: String?,
        manufactureYear: Int?,
        modelYear: Int?,
        category: String?,
        city: String?,
        state: String?,
        restrictionStatus: String,
        restrictionDescription: String?,
        sourceName: String?
    )

    @Query(
        "SELECT * FROM vehicle_records WHERE nmtNextAttemptAt <= :now " +
            "ORDER BY nmtNextAttemptAt ASC, lastSeenTimestamp DESC LIMIT 1"
    )
    suspend fun nextNmtCandidate(now: Long): VehicleRecordEntity?

    @Query(
        "UPDATE vehicle_records SET nmtNextAttemptAt = :leaseUntil, " +
            "nmtAttempts = nmtAttempts + 1 WHERE id = :id"
    )
    suspend fun claimNmt(id: Long, leaseUntil: Long)

    @Query(
        "UPDATE vehicle_records SET nmtStatus = :status, nmtCheckedAt = :checkedAt, " +
            "nmtRawStatus = :rawStatus, nmtAttempts = :attempts, " +
            "nmtNextAttemptAt = :nextAttemptAt WHERE id = :id"
    )
    suspend fun updateNmtResult(
        id: Long,
        status: String,
        checkedAt: Long,
        rawStatus: String?,
        attempts: Int,
        nextAttemptAt: Long
    )

    /**
     * Atualiza apenas o estado da tentativa, preservando a última categoria
     * concluída. Usado quando a consulta falha depois de já existir um
     * resultado REGULAR, IRREGULAR ou NÃO CADASTRADO.
     */
    @Query(
        "UPDATE vehicle_records SET nmtCheckedAt = :checkedAt, " +
            "nmtRawStatus = :rawStatus, nmtAttempts = :attempts, " +
            "nmtNextAttemptAt = :nextAttemptAt WHERE id = :id"
    )
    suspend fun updateNmtMetadata(
        id: Long,
        checkedAt: Long,
        rawStatus: String?,
        attempts: Int,
        nextAttemptAt: Long
    )

    @Query(
        "UPDATE vehicle_records SET nmtNextAttemptAt = -1, nmtAttempts = 0 " +
            "WHERE id = :id"
    )
    suspend fun resetNmtQueue(id: Long)

    /**
     * Reabre uma vez, por versão do app, a fila de registros que ficaram sem
     * categoria por falhas antigas. Não toca em categorias já concluídas.
     */
    @Query(
        "UPDATE vehicle_records SET nmtNextAttemptAt = 0, nmtAttempts = 0 " +
            "WHERE nmtStatus IS NULL OR nmtStatus IN ('UNAVAILABLE', 'UNKNOWN')"
    )
    suspend fun resetUnresolvedNmtQueue(): Int

    @Query("DELETE FROM vehicle_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM vehicle_records")
    suspend fun deleteAll()

    @Query(
        "SELECT photoPath FROM vehicle_records WHERE photoPath IS NOT NULL " +
            "UNION SELECT plateCropPath FROM vehicle_records WHERE plateCropPath IS NOT NULL"
    )
    suspend fun allPhotoPaths(): List<String>
}
