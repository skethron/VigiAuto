package br.com.vigiauto.data.lookup

import br.com.vigiauto.domain.model.QueryStatus
import br.com.vigiauto.domain.model.RestrictionStatus
import br.com.vigiauto.domain.model.VehicleData
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue

/**
 * Cliente de DEMONSTRAÇÃO. Os dados retornados são fictícios e sempre
 * identificados como tal na interface e no campo sourceName.
 * Não representa o serviço real usado pela Consulta Simples.
 */
class DemoConsultaPlacaService : ConsultaPlacaService {

    override val displayName: String = SOURCE_NAME
    override val isDemo: Boolean = true

    private data class DemoVehicle(
        val brand: String,
        val model: String,
        val version: String?,
        val color: String,
        val manufactureYear: Int,
        val modelYear: Int,
        val category: String,
        val city: String = "São Paulo",
        val state: String = "SP"
    )

    private val vehicles = listOf(
        DemoVehicle("Fiat", "Cronos", "Drive 1.3", "Prata", 2022, 2023, "Automóvel"),
        DemoVehicle("Volkswagen", "T-Cross", "Comfortline 200 TSI", "Azul", 2021, 2022, "Automóvel"),
        DemoVehicle("Hyundai", "HB20", "Comfort 1.0", "Branco", 2020, 2021, "Automóvel"),
        DemoVehicle("Chevrolet", "Spin", "LTZ 1.8", "Prata", 2019, 2020, "Automóvel"),
        DemoVehicle("Honda", "CG 160", "Fan", "Vermelha", 2023, 2023, "Motocicleta"),
        DemoVehicle("Yamaha", "Factor 150", "ED", "Preta", 2022, 2022, "Motocicleta"),
        DemoVehicle("Toyota", "Corolla", "XEi 2.0", "Preto", 2021, 2022, "Automóvel"),
        DemoVehicle("Fiat", "Mobi", "Like 1.0", "Branco", 2023, 2024, "Automóvel")
    )

    override suspend fun lookup(plate: String): LookupResult {
        delay(1_100)
        val index = plate.hashCode().absoluteValue % vehicles.size
        val vehicle = vehicles[index]
        val lastDigit = plate.lastOrNull()?.digitToIntOrNull() ?: 0

        val restrictionStatus: RestrictionStatus
        val restrictionDescription: String?
        when {
            lastDigit == 0 -> {
                restrictionStatus = RestrictionStatus.CONFIRMED
                restrictionDescription = "Restrição administrativa (exemplo fictício de demonstração)."
            }
            lastDigit in 3..4 -> {
                restrictionStatus = RestrictionStatus.INCONCLUSIVE
                restrictionDescription = "A fonte de demonstração não permitiu concluir a situação."
            }
            else -> {
                restrictionStatus = RestrictionStatus.NONE_IDENTIFIED
                restrictionDescription = "Nenhuma restrição identificada na fonte (exemplo fictício)."
            }
        }

        return LookupResult.Success(
            VehicleData(
                brand = vehicle.brand,
                model = vehicle.model,
                version = vehicle.version,
                color = vehicle.color,
                manufactureYear = vehicle.manufactureYear,
                modelYear = vehicle.modelYear,
                vehicleCategory = vehicle.category,
                city = vehicle.city,
                state = vehicle.state,
                restrictionStatus = restrictionStatus,
                restrictionDescription = restrictionDescription,
                sourceName = SOURCE_NAME,
                incomplete = false
            )
        )
    }

    override suspend fun testConnection(): ConnectionTestResult {
        delay(300)
        return ConnectionTestResult(
            ok = true,
            detail = "Modo demonstração ativo. Nenhuma chamada externa é realizada e os dados exibidos são fictícios."
        )
    }

    companion object {
        const val SOURCE_NAME = "DEMO — dados fictícios"
    }
}
