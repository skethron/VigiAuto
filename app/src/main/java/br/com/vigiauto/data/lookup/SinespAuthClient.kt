package br.com.vigiauto.data.lookup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder

sealed interface SinespLoginResult {
    data class Success(
        val token: String,
        val expiresAtEpochMs: Long?,
        val warning: String? = null
    ) : SinespLoginResult

    data class Failure(val message: String) : SinespLoginResult
}

/**
 * Conclui o login gov.br do SINESP: troca o `code` de uso único devolvido
 * pelo WebView pelo token pessoal e confere o token em `GET /me`.
 *
 * O token nunca é gravado aqui; o chamador o entrega ao
 * [br.com.vigiauto.data.prefs.SettingsRepository], que cifra com o Android
 * Keystore. Nenhum corpo de resposta, token ou código é impresso em log.
 */
class SinespAuthClient(
    private val client: OkHttpClient = SinespV1Source.defaultClient(),
    private val baseUrl: String = SinespV1Protocol.BASE_URL,
    private val clockMs: () -> Long = System::currentTimeMillis
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun exchangeCode(code: String): SinespLoginResult = withContext(Dispatchers.IO) {
        val normalizedCode = code.trim()
        if (normalizedCode.isBlank()) {
            return@withContext SinespLoginResult.Failure(
                "O gov.br não retornou um código de login."
            )
        }

        val url = buildString {
            append(baseUrl.trimEnd('/'))
            append("/auth/token?code=")
            append(URLEncoder.encode(normalizedCode, "UTF-8"))
        }
        val request = Request.Builder()
            .url(url)
            .post(ByteArray(0).toRequestBody(null))
            .apply {
                authHeaders().forEach { (name, value) -> header(name, value) }
            }
            .build()

        try {
            val token = client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.code != 200) {
                    return@withContext SinespLoginResult.Failure(
                        tokenExchangeError(response.code)
                    )
                }

                val rawToken = parseToken(body)
                    ?: return@withContext SinespLoginResult.Failure(
                        "O login foi concluído, mas o SINESP não devolveu um token válido."
                    )
                SinespV1Protocol.normalizeToken(rawToken)
                    ?: return@withContext SinespLoginResult.Failure(
                        "O login foi concluído, mas o SINESP não devolveu um token válido."
                    )
            }

            when (val validation = validateToken(token)) {
                is Validation.Rejected -> SinespLoginResult.Failure(validation.message)
                is Validation.Warning -> SinespLoginResult.Success(
                    token = token,
                    expiresAtEpochMs = SinespV1Protocol.tokenExpiryEpochMs(token),
                    warning = validation.message
                )
                Validation.Ok -> SinespLoginResult.Success(
                    token = token,
                    expiresAtEpochMs = SinespV1Protocol.tokenExpiryEpochMs(token)
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: IOException) {
            SinespLoginResult.Failure(
                "Falha de conexão ao concluir o login. Verifique a internet e tente novamente."
            )
        } catch (_: Exception) {
            SinespLoginResult.Failure(
                "Não foi possível concluir o login gov.br. Tente novamente."
            )
        }
    }

    private suspend fun validateToken(token: String): Validation {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/me")
            .get()
            .apply {
                authHeaders(token).forEach { (name, value) -> header(name, value) }
            }
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 200 -> Validation.Ok
                    response.code == 401 || response.code == 403 -> Validation.Rejected(
                        "O SINESP recusou o token emitido. O código pode ter expirado; " +
                            "faça o login novamente."
                    )
                    else -> Validation.Warning(
                        "Token salvo, mas o SINESP não confirmou o login agora " +
                            "(HTTP ${response.code}). A primeira consulta vai validar o token."
                    )
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: IOException) {
            Validation.Warning(
                "Token salvo, mas não foi possível confirmar o login agora. " +
                    "A primeira consulta vai validar o token."
            )
        } catch (_: Exception) {
            Validation.Warning(
                "Token salvo, mas não foi possível confirmar o login agora. " +
                    "A primeira consulta vai validar o token."
            )
        }
    }

    private fun authHeaders(token: String? = null): Map<String, String> = buildMap {
        put("Accept", "application/json")
        put("User-Agent", SinespV1Protocol.USER_AGENT)
        put("AppEnsurer", SinespV1Protocol.appEnsurer(clockMs()))
        if (token != null) put("Authorization", token)
    }

    private fun parseToken(body: String?): String? {
        val raw = body?.trim().orEmpty()
        if (raw.isBlank()) return null
        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull()
        return when (element) {
            is JsonPrimitive -> element.content.trim().takeIf { it.isNotBlank() }
            is JsonObject -> listOf("token", "access_token", "data", "value")
                .firstNotNullOfOrNull { key ->
                    (element[key] as? JsonPrimitive)
                        ?.content
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                }
            else -> raw.takeIf { !it.startsWith("{") && !it.startsWith("[") }
        }
    }

    private fun tokenExchangeError(code: Int): String = when {
        code == 401 || code == 403 -> "O SINESP recusou o código de login " +
            "(HTTP $code). Ele é de uso único e expira rápido; faça o login novamente."
        code == 429 -> "Muitas tentativas de login em pouco tempo. Aguarde e tente novamente."
        code in 500..599 -> "O SINESP está indisponível no momento (HTTP $code). " +
            "Tente novamente mais tarde."
        else -> "O SINESP recusou o login (HTTP $code). Tente novamente."
    }

    private sealed interface Validation {
        data object Ok : Validation
        data class Warning(val message: String) : Validation
        data class Rejected(val message: String) : Validation
    }
}
