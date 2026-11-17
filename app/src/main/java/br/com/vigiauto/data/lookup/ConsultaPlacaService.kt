package br.com.vigiauto.data.lookup

import br.com.vigiauto.data.prefs.SettingsRepository
import br.com.vigiauto.domain.model.LookupMode
import br.com.vigiauto.domain.model.QueryStatus
import br.com.vigiauto.domain.model.VehicleData
import kotlinx.coroutines.flow.first

sealed interface LookupResult {
    data class Success(val data: VehicleData) : LookupResult
    data class Failure(val status: QueryStatus, val message: String?) : LookupResult
}

data class ConnectionTestResult(val ok: Boolean, val detail: String)

interface ConsultaPlacaService {
    val displayName: String
    val isDemo: Boolean

    suspend fun lookup(plate: String): LookupResult

    suspend fun testConnection(): ConnectionTestResult
}

/**
 * Encaminha as consultas para o modo configurado: Consulta Simples (remote
 * config), Sinesp (fonte autorizada), HTTP manual ou demonstração. A UI nunca
 * depende de um cliente concreto.
 *
 * Quando o modo é Sinesp mas não há token salvo, [lookup] usa a Consulta
 * Simples automaticamente. [testConnection] continua testando o Sinesp
 * selecionado, para o botão "Verificar configuração" não dar um falso positivo.
 */
class LookupRouter(
    private val settings: SettingsRepository,
    private val demo: ConsultaPlacaService,
    private val remote: ConsultaPlacaService,
    private val sinesp: ConsultaPlacaService,
    private val http: ConsultaPlacaService
) {

    private suspend fun selectedService(): ConsultaPlacaService =
        when (settings.settingsFlow.first().lookupMode) {
            LookupMode.REMOTE -> remote
            LookupMode.SINESP -> sinesp
            LookupMode.HTTP -> http
            LookupMode.DEMO -> demo
        }

    private suspend fun lookupService(): ConsultaPlacaService {
        val config = settings.settingsFlow.first()
        if (shouldUseSimpleFallback(config.lookupMode, config.hasSinespToken)) return remote
        return when (config.lookupMode) {
            LookupMode.REMOTE -> remote
            LookupMode.SINESP -> sinesp
            LookupMode.HTTP -> http
            LookupMode.DEMO -> demo
        }
    }

    /** Serviço selecionado nas configurações, sem aplicar o fallback. */
    suspend fun current(): ConsultaPlacaService = selectedService()

    suspend fun lookup(plate: String): LookupResult = lookupService().lookup(plate)

    suspend fun testConnection(): ConnectionTestResult = selectedService().testConnection()

    suspend fun isDemo(): Boolean = selectedService().isDemo

    companion object {
        /**
         * O Sinesp é o modo padrão; sem token ele não consegue consultar.
         * Nesse caso a leitura usa a Consulta Simples em vez de falhar.
         */
        fun shouldUseSimpleFallback(mode: LookupMode, hasSinespToken: Boolean): Boolean =
            mode == LookupMode.SINESP && !hasSinespToken
    }
}
