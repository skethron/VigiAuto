package br.com.vigiauto.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vehicle_records",
    indices = [Index(value = ["normalizedPlate"], unique = true)]
)
data class VehicleRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val normalizedPlate: String,
    val displayPlate: String,
    val brand: String? = null,
    val model: String? = null,
    val version: String? = null,
    val color: String? = null,
    val manufactureYear: Int? = null,
    val modelYear: Int? = null,
    val vehicleCategory: String? = null,
    val city: String? = null,
    val state: String? = null,
    val locationStreet: String? = null,
    val locationNumber: String? = null,
    val locationNeighborhood: String? = null,
    val locationCity: String? = null,
    val locationState: String? = null,
    val locationPostalCode: String? = null,
    val locationCapturedAt: Long? = null,
    val nmtStatus: String? = null,
    val nmtCheckedAt: Long? = null,
    val nmtRawStatus: String? = null,
    val nmtAttempts: Int = 0,
    val nmtNextAttemptAt: Long = 0,
    val restrictionStatus: String = "UNKNOWN",
    val restrictionDescription: String? = null,
    val sourceName: String? = null,
    val firstDetectionTimestamp: Long,
    val lastSeenTimestamp: Long,
    val queryTimestamp: Long? = null,
    val queryStatus: String = "PENDING",
    val recognitionConfidence: Float = 0f,
    val occurrenceCount: Int = 1,
    val photoPath: String? = null,
    val photoCaptureTimestamp: Long? = null,
    val photoQualityStatus: String = "UNAVAILABLE",
    val plateCropPath: String? = null,
    val plateOcrText: String? = null,
    val plateBoxLeft: Float? = null,
    val plateBoxTop: Float? = null,
    val plateBoxRight: Float? = null,
    val plateBoxBottom: Float? = null,
    val plateDetectorConfidence: Float? = null,
    val plateOcrConfidence: Float? = null,
    val plateCropAspectRatio: Float? = null,
    val plateQualityStatus: String = "UNAVAILABLE",
    val plateNeedsReview: Boolean = false
)
