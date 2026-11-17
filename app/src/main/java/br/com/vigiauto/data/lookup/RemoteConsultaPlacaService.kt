package br.com.vigiauto.data.lookup

import br.com.vigiauto.domain.model.QueryStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Cliente real do serviço Consulta Placa.
 *
 * Contrato observado e validado em 19/10/2026:
 * - GET https://placa.costaw.com.br/api?placa=<PLACA>&token=<TOKEN>
 * - 200: JSON com placa, marca, modelo, ano, anoModelo, cor, situacao e extra
 *   (restricao_1..4, tipo_veiculo, especie_veiculo, etc.);
 * - 404: {"message":"Veículo não encontrado"}.
 *
 * A URL e o token vêm do remote config, em runtime. Nenhuma credencial é
 * embutida no APK e nenhuma imagem é enviada ao serviço.
 */
class RemoteConsultaPlacaService(
    private val remoteConfig: RemoteConfigService,
    private val client: OkHttpClient = RemoteConfigService.defaultClient()
) : ConsultaPlacaService {

    override val displayName: String = "Consulta Simples (API)"
    override val isDemo: Boolean = false

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    override suspend fun lookup(plate: String): LookupResult = withContext(Dispatchers.IO) {
        val template = remoteConfig.apiTemplate()
            ?: return@withContext LookupResult.Failure(
                QueryStatus.CONNECTION_FAILURE,
                "Não foi possível carregar a configuração do serviço de consulta."
            )

        val url = RemoteApiConfig.forPlate(template, plate)
            ?: return@withContext LookupResult.Failure(
                QueryStatus.INVALID_RESPONSE,
                "Configuração do serviço inválida."
            )

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", "VigiAuto/1.1 (Android)")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()

                when {
                    response.code == 404 -> LookupResult.Failure(
                        QueryStatus.NOT_FOUND,
                        RemoteApiConfig.messageFrom(body) ?: "Veículo não encontrado na base consultada."
                    )

                    response.code == 401 || response.code == 403 -> {
                        // O token pode ter sido rotacionado: a próxima consulta
                        // busca uma configuração nova.
                        remoteConfig.invalidate()
                        LookupResult.Failure(
                            QueryStatus.AUTH_ERROR,
                            "O serviço recusou a credencial de acesso. Tente novamente em instantes."
                        )
                    }

                    response.code == 429 -> LookupResult.Failure(
                        QueryStatus.RATE_LIMITED,
                        "Limite de consultas do serviço atingido. Aguarde antes de tentar novamente."
                    )

                    !response.isSuccessful -> LookupResult.Failure(
                        QueryStatus.INVALID_RESPONSE,
                        "O serviço respondeu com HTTP ${response.code}."
                    )

                    body.isNullOrBlank() -> LookupResult.Failure(
                        QueryStatus.INVALID_RESPONSE,
                        "Resposta vazia do serviço."
                    )

                    else -> {
                        val root = runCatching {
                            json.parseToJsonElement(body) as? JsonObject
                        }.getOrNull()

                        if (root == null) {
                            LookupResult.Failure(
                                QueryStatus.INVALID_RESPONSE,
                                "Formato de resposta não reconhecido."
                            )
                        } else {
                            val mapped = VehicleResponseMapper.map(
                                root = root,
                                defaultSource = displayName
                            )
                            when {
                                mapped != null -> LookupResult.Success(mapped)
                                isNotFoundPayload(root) -> LookupResult.Failure(
                                    QueryStatus.NOT_FOUND,
                                    RemoteApiConfig.messageFrom(body)
                                        ?: "Veículo não encontrado na base consultada."
                                )
                                else -> LookupResult.Failure(
                                    QueryStatus.INVALID_RESPONSE,
                                    "Formato de resposta não reconhecido."
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: IOException) {
            LookupResult.Failure(
                QueryStatus.CONNECTION_FAILURE,
                "Falha de conexão com o serviço de consulta."
            )
        } catch (_: Exception) {
            LookupResult.Failure(
                QueryStatus.INVALID_RESPONSE,
                "Resposta inválida do serviço de consulta."
            )
        }
    }

    override suspend fun testConnection(): ConnectionTestResult = withContext(Dispatchers.IO) {
        val template = remoteConfig.apiTemplate(forceRefresh = true)
            ?: return@withContext ConnectionTestResult(
                false,
                "Não foi possível carregar a configuração remota. Verifique a conexão."
            )

        val host = RemoteApiConfig.hostOf(template)
            ?: return@withContext ConnectionTestResult(false, "Configuração remota inválida.")

        ConnectionTestResult(
            true,
            "Configuração carregada de $host. O token é obtido em runtime e não fica no APK."
        )
    }

    private fun isNotFoundPayload(root: JsonObject): Boolean {
        val message = (root["message"]?.toString() ?: "").lowercase()
        return "encontrado" in message || "not found" in message
    }
}
