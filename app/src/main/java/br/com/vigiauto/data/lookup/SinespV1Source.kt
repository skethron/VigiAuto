package br.com.vigiauto.data.lookup

import br.com.vigiauto.domain.model.QueryStatus
import br.com.vigiauto.domain.plate.PlateNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Credenciais e identificadores necessários para falar com o SINESP v1. */
data class SinespCredentials(
    val token: String?,
    val geo: String,
    val uniqueId: String
)

/**
 * Cliente da consulta v1 do SINESP Cidadão.
 *
 * O token e a localização são lidos do armazenamento local a cada consulta,
 * então salvar um token novo em Configurações já vale para a próxima placa,
 * sem reiniciar o aplicativo. O token é gravado cifrado pelo Android Keystore
 * em [br.com.vigiauto.data.prefs.SettingsRepository].
 */
class SinespV1Source(
    private val credentials: suspend () -> SinespCredentials,
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: String = SinespV1Protocol.BASE_URL,
    private val clockMs: () -> Long = System::currentTimeMillis
) : SinespSource {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    override suspend fun lookup(plate: String): LookupResult = withContext(Dispatchers.IO) {
        val normalizedPlate = PlateNormalizer.normalize(plate)
            ?: return@withContext LookupResult.Failure(
                QueryStatus.INVALID_RESPONSE,
                "Placa inválida. Use o formato ABC1234 ou ABC1D23."
            )

        val config = runCatching { credentials() }.getOrElse {
            return@withContext LookupResult.Failure(
                QueryStatus.UNAVAILABLE,
                "Não foi possível ler o token do Sinesp salvo no aparelho."
            )
        }
        val token = SinespV1Protocol.normalizeToken(config.token)
            ?: return@withContext LookupResult.Failure(
                QueryStatus.UNAVAILABLE,
                TOKEN_MISSING_MESSAGE
            )
        val geo = SinespV1Protocol.normalizeGeo(config.geo)
            ?: SinespV1Protocol.DEFAULT_GEO
        val uniqueId = config.uniqueId.trim().ifBlank { "vigiauto" }
        val url = "${baseUrl.trim().trimEnd('/')}/vehicles/$normalizedPlate"

        var lastNetworkFailure: IOException? = null

        for (attempt in 1..MAX_ATTEMPTS) {
            val request = Request.Builder()
                .url(url)
                .get()
                .apply {
                    SinespV1Protocol.headers(
                        token = token,
                        uniqueId = uniqueId,
                        geo = geo,
                        timestampMs = clockMs()
                    ).forEach { (name, value) -> header(name, value) }
                }
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    when {
                        response.code == 200 ->
                            return@withContext parseSuccess(body)

                        response.code == 401 || response.code == 403 ->
                            return@withContext authFailure(body)

                        response.code == 404 -> return@withContext LookupResult.Failure(
                            QueryStatus.NOT_FOUND,
                            "Veículo não encontrado na base do SINESP."
                        )

                        response.code == 429 -> return@withContext LookupResult.Failure(
                            QueryStatus.RATE_LIMITED,
                            "O SINESP limitou as consultas (HTTP 429). Aguarde antes de tentar novamente."
                        )

                        response.code == 400 -> return@withContext badRequest(body)

                        response.code in 500..599 && attempt < MAX_ATTEMPTS -> Unit

                        response.code in 500..599 -> return@withContext LookupResult.Failure(
                            QueryStatus.CONNECTION_FAILURE,
                            "O SINESP respondeu com HTTP ${response.code}. Tente novamente mais tarde."
                        )

                        else -> return@withContext LookupResult.Failure(
                            QueryStatus.INVALID_RESPONSE,
                            "O SINESP respondeu com HTTP ${response.code}."
                        )
                    }
                }
            } catch (error: IOException) {
                lastNetworkFailure = error
                if (attempt >= MAX_ATTEMPTS) break
            } catch (_: Exception) {
                return@withContext LookupResult.Failure(
                    QueryStatus.INVALID_RESPONSE,
                    "Falha ao interpretar a resposta do SINESP."
                )
            }
        }

        LookupResult.Failure(
            QueryStatus.CONNECTION_FAILURE,
            if (lastNetworkFailure != null) {
                "Falha de conexão com o SINESP. Verifique a internet e tente novamente."
            } else {
                "Não foi possível concluir a consulta ao SINESP."
            }
        )
    }

    /**
     * O v1 não tem endpoint de saúde. Validar a configuração evita gastar uma
     * consulta real só para testar; o token é conferido na primeira placa.
     */
    override suspend fun testConnection(): ConnectionTestResult = withContext(Dispatchers.IO) {
        val config = runCatching { credentials() }.getOrElse {
            return@withContext ConnectionTestResult(
                false,
                "Não foi possível ler a configuração local do Sinesp."
            )
        }
        SinespV1Protocol.normalizeToken(config.token)
            ?: return@withContext ConnectionTestResult(false, TOKEN_MISSING_MESSAGE)
        SinespV1Protocol.normalizeGeo(config.geo)
            ?: return@withContext ConnectionTestResult(
                false,
                "Localização inválida. Use longitude,latitude (ex.: -47.8825,-15.7942)."
            )

        ConnectionTestResult(
            true,
            "Token e localização configurados. A validação do token acontece na primeira " +
                "consulta; ele é pessoal e pode expirar."
        )
    }

    private fun parseSuccess(body: String?): LookupResult {
        if (body.isNullOrBlank()) {
            return LookupResult.Failure(
                QueryStatus.INVALID_RESPONSE,
                "Resposta vazia do SINESP."
            )
        }
        val root = runCatching {
            json.parseToJsonElement(body) as? JsonObject
        }.getOrNull() ?: return LookupResult.Failure(
            QueryStatus.INVALID_RESPONSE,
            "Resposta do SINESP em formato não reconhecido."
        )

        val data = SinespV1Protocol.mapResponse(root)
            ?: return LookupResult.Failure(
                QueryStatus.INVALID_RESPONSE,
                "O SINESP respondeu, mas sem os campos esperados do veículo."
            )
        return LookupResult.Success(data)
    }

    private fun authFailure(body: String?): LookupResult.Failure {
        val serverMessage = messageFrom(body)
        val detail = buildString {
            append("O SINESP recusou o token do gov.br")
            if (!serverMessage.isNullOrBlank()) append(" ($serverMessage)")
            append(". O token é pessoal e expira; renove pelo botão Login gov.br em Configurações.")
        }
        return LookupResult.Failure(QueryStatus.AUTH_ERROR, detail)
    }

    private fun badRequest(body: String?): LookupResult.Failure {
        val serverMessage = messageFrom(body)
        val mentionsGeo = body?.contains("geolocation", ignoreCase = true) == true ||
            serverMessage?.contains("geolocation", ignoreCase = true) == true
        val detail = if (mentionsGeo) {
            "O SINESP recusou a localização enviada. Confira a localização " +
                "(longitude,latitude) em Configurações."
        } else {
            "O SINESP recusou a consulta (HTTP 400)" +
                if (serverMessage.isNullOrBlank()) "." else ": $serverMessage"
        }
        return LookupResult.Failure(QueryStatus.INVALID_RESPONSE, detail)
    }

    private fun messageFrom(body: String?): String? {
        if (body.isNullOrBlank()) return null
        val root = runCatching {
            json.parseToJsonElement(body) as? JsonObject
        }.getOrNull() ?: return null
        return (root["message"] as? JsonPrimitive)
            ?.content
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    companion object {
        private const val MAX_ATTEMPTS = 2

        const val TOKEN_MISSING_MESSAGE =
            "Token do gov.br não configurado. Abra Configurações → Sinesp e toque em " +
                "Login gov.br (ou cole um token já obtido). Sem token, as leituras usam a " +
                "Consulta Simples. Nenhuma consulta ao SINESP foi feita."

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}
