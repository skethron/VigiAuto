package br.com.vigiauto.domain.model

data class VehicleData(
    val brand: String? = null,
    val model: String? = null,
    val version: String? = null,
    val color: String? = null,
    val manufactureYear: Int? = null,
    val modelYear: Int? = null,
    val vehicleCategory: String? = null,
    val city: String? = null,
    val state: String? = null,
    val restrictionStatus: RestrictionStatus = RestrictionStatus.UNKNOWN,
    val restrictionDescription: String? = null,
    val sourceName: String,
    val incomplete: Boolean = false
)
