package br.com.vigiauto.domain.model

data class VehicleRecord(
    val id: Long,
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
    // Localização do aparelho no momento da consulta. Não se confunde com
    // city/state, que são dados do veículo devolvidos pela fonte.
    val locationStreet: String? = null,
    val locationNumber: String? = null,
    val locationNeighborhood: String? = null,
    val locationCity: String? = null,
    val locationState: String? = null,
    val locationPostalCode: String? = null,
    val locationCapturedAt: Long? = null,
    val nmtStatus: NmtStatus? = null,
    val nmtCheckedAt: Long? = null,
    val restrictionStatus: RestrictionStatus = RestrictionStatus.UNKNOWN,
    val restrictionDescription: String? = null,
    val sourceName: String? = null,
    val firstDetectionTimestamp: Long,
    val lastSeenTimestamp: Long,
    val queryTimestamp: Long? = null,
    val queryStatus: QueryStatus = QueryStatus.PENDING,
    val recognitionConfidence: Float = 0f,
    val occurrenceCount: Int = 1,
    val photoPath: String? = null,
    val photoCaptureTimestamp: Long? = null,
    val photoQualityStatus: PhotoQuality = PhotoQuality.UNAVAILABLE,
    val plateCropPath: String? = null,
    val plateOcrText: String? = null,
    val plateBoxLeft: Float? = null,
    val plateBoxTop: Float? = null,
    val plateBoxRight: Float? = null,
    val plateBoxBottom: Float? = null,
    val plateDetectorConfidence: Float? = null,
    val plateOcrConfidence: Float? = null,
    val plateCropAspectRatio: Float? = null,
    val plateQualityStatus: PlateQuality = PlateQuality.UNAVAILABLE,
    val plateNeedsReview: Boolean = false
) {
    val hasVehicleData: Boolean
        get() = !brand.isNullOrBlank() || !model.isNullOrBlank() || !color.isNullOrBlank()

    val vehicleSummary: String?
        get() {
            val parts = listOfNotNull(
                listOfNotNull(brand, model).joinToString(" ").takeIf { it.isNotBlank() },
                color,
                listOfNotNull(manufactureYear, modelYear).distinct().joinToString("/").takeIf { it.isNotBlank() }
            )
            return parts.takeIf { it.isNotEmpty() }?.joinToString(" • ")
        }

    val hasLocation: Boolean
        get() = !locationStreet.isNullOrBlank() || !locationNumber.isNullOrBlank() ||
            !locationNeighborhood.isNullOrBlank() || !locationCity.isNullOrBlank() ||
            !locationState.isNullOrBlank() || !locationPostalCode.isNullOrBlank()

    val locationStreetLine: String?
        get() = listOfNotNull(
            locationStreet?.takeIf { it.isNotBlank() },
            locationNumber?.takeIf { it.isNotBlank() }
        ).joinToString(", ").takeIf { it.isNotBlank() }

    val locationCityLine: String?
        get() = listOfNotNull(
            locationCity?.takeIf { it.isNotBlank() },
            locationState?.takeIf { it.isNotBlank() }
        ).joinToString("/").takeIf { it.isNotBlank() }

    val locationSummary: String?
        get() {
            if (!hasLocation) return null
            val parts = listOfNotNull(
                locationStreetLine,
                locationNeighborhood?.takeIf { it.isNotBlank() },
                locationCityLine,
                locationPostalCode?.takeIf { it.isNotBlank() }?.let { "CEP $it" }
            )
            return parts.joinToString(" • ").takeIf { it.isNotBlank() }
        }

    /**
     * Endereço completo em um único parágrafo, sem data de captura.
     * Usado na tela de detalhes e no card compartilhado.
     */
    val locationFullLine: String?
        get() {
            if (!hasLocation) return null
            val parts = listOfNotNull(
                locationStreetLine,
                locationNeighborhood?.takeIf { it.isNotBlank() },
                locationCityLine,
                locationPostalCode?.takeIf { it.isNotBlank() }?.let { "CEP $it" }
            )
            return parts.joinToString(", ").takeIf { it.isNotBlank() }
        }
}
