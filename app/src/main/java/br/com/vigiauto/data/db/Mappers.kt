package br.com.vigiauto.data.db

import br.com.vigiauto.domain.model.NmtStatus
import br.com.vigiauto.domain.model.PhotoQuality
import br.com.vigiauto.domain.model.PlateQuality
import br.com.vigiauto.domain.model.QueryStatus
import br.com.vigiauto.domain.model.RestrictionStatus
import br.com.vigiauto.domain.model.VehicleRecord
import br.com.vigiauto.domain.model.normalizeOccurrenceDates

fun VehicleRecordEntity.toDomain(): VehicleRecord = VehicleRecord(
    id = id,
    normalizedPlate = normalizedPlate,
    displayPlate = displayPlate,
    brand = brand,
    model = model,
    version = version,
    color = color,
    manufactureYear = manufactureYear,
    modelYear = modelYear,
    vehicleCategory = vehicleCategory,
    city = city,
    state = state,
    locationStreet = locationStreet,
    locationNumber = locationNumber,
    locationNeighborhood = locationNeighborhood,
    locationCity = locationCity,
    locationState = locationState,
    locationPostalCode = locationPostalCode,
    locationCapturedAt = locationCapturedAt,
    nmtStatus = enumOrNull<NmtStatus>(nmtStatus),
    nmtCheckedAt = nmtCheckedAt,
    restrictionStatus = enumOrElse(restrictionStatus, RestrictionStatus.UNKNOWN),
    restrictionDescription = normalizeOccurrenceDates(restrictionDescription),
    sourceName = sourceName,
    firstDetectionTimestamp = firstDetectionTimestamp,
    lastSeenTimestamp = lastSeenTimestamp,
    queryTimestamp = queryTimestamp,
    queryStatus = enumOrElse(queryStatus, QueryStatus.PENDING),
    recognitionConfidence = recognitionConfidence,
    occurrenceCount = occurrenceCount,
    photoPath = photoPath,
    photoCaptureTimestamp = photoCaptureTimestamp,
    photoQualityStatus = enumOrElse(photoQualityStatus, PhotoQuality.UNAVAILABLE),
    plateCropPath = plateCropPath,
    plateOcrText = plateOcrText,
    plateBoxLeft = plateBoxLeft,
    plateBoxTop = plateBoxTop,
    plateBoxRight = plateBoxRight,
    plateBoxBottom = plateBoxBottom,
    plateDetectorConfidence = plateDetectorConfidence,
    plateOcrConfidence = plateOcrConfidence,
    plateCropAspectRatio = plateCropAspectRatio,
    plateQualityStatus = enumOrElse(plateQualityStatus, PlateQuality.UNAVAILABLE),
    plateNeedsReview = plateNeedsReview
)

private inline fun <reified T : Enum<T>> enumOrElse(value: String?, fallback: T): T =
    value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

private inline fun <reified T : Enum<T>> enumOrNull(value: String?): T? =
    value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
