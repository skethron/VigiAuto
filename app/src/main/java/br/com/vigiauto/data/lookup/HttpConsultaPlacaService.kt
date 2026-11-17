package br.com.vigiauto.data.lookup

import android.util.Log
import br.com.vigiauto.data.prefs.SettingsRepository
import br.com.vigiauto.domain.model.QueryStatus
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.QueryMap
import retrofit2.http.Url
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Cliente HTTP MANUAL, para endpoints alternativos ou testes controlados.
 *
 * O modo padrão do app é a API real (REMOTE), que usa a URL/token do remote
 * config. Este cliente permanece disponível para quem precisa apontar para
 * outro endpoint autorizado. URL, caminho, parâmetro e token são configuráveis;
 * o mapeamento da resposta é tolerante e sinaliza dados incompletos.
 */
class HttpConsultaPlacaService(
    private val settings: SettingsRepository
) : ConsultaPlacaService {

    override val displayName: String = "Consulta Placa (HTTP provisório)"
    override val isDemo: Boolean = false

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    private val api: ConsultaPlacaApi = Retrofit.Builder()
        .baseUrl("https://placeholder.invalid/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ConsultaPlacaApi::class.java)

    override suspend fun lookup(plate: String): LookupResult {
        val config = settings.settingsFlow.first()
        val url = buildUrl(config.httpBaseUrl, config.httpPath)
            ?: return LookupResult.Failure(QueryStatus.INVALID_RESPONSE, "URL de integração inválida.")

        if (!isAllowedUrl(url)) {
            return LookupResult.Failure(QueryStatus.INVALID_RESPONSE, "A URL deve usar HTTPS.")
        }

        val token = settings.httpToken()
        val headers = buildMap {
            put("Accept", "application/json")
            put("User-Agent", "VigiAuto/1.0 (Android)")
            if (!token.isNullOrBlank()) put("Authorization", "Bearer $token")
        }
        val parameters = mapOf(config.httpPlateParam.ifBlank { "placa" } to plate)

        return try {
            val response: Response<JsonObject> = api.lookup(url, parameters, headers)
            if (!response.isSuccessful) {
                val status = when (response.code()) {
                    404 -> QueryStatus.NOT_FOUND
                    401, 403 -> QueryStatus.AUTH_ERROR
                    429 -> QueryStatus.RATE_LIMITED
                    else -> QueryStatus.INVALID_RESPONSE
                }
                LookupResult.Failure(status, "HTTP ${response.code()}")
            } else {
                val body = response.body()
                if (body == null) {
                    LookupResult.Failure(QueryStatus.INVALID_RESPONSE, "Resposta vazia do serviço.")
                } else {
                    val mapped = VehicleResponseMapper.map(body, displayName)
                    if (mapped == null) {
                        LookupResult.Failure(QueryStatus.INVALID_RESPONSE, "Formato de resposta não reconhecido.")
                    } else {
                        Log.i(TAG, "Consulta concluída (${if (mapped.incomplete) "incompleta" else "completa"}).")
                        LookupResult.Success(mapped)
                    }
                }
            }
        } catch (error: IOException) {
            LookupResult.Failure(QueryStatus.CONNECTION_FAILURE, error.message)
        } catch (error: Exception) {
            LookupResult.Failure(QueryStatus.INVALID_RESPONSE, error.message)
        }
    }

    override suspend fun testConnection(): ConnectionTestResult = withContext(Dispatchers.IO) {
        val config = settings.settingsFlow.first()
        val baseUrl = buildUrl(config.httpBaseUrl, "")
            ?: return@withContext ConnectionTestResult(false, "URL de integração inválida.")
        if (!isAllowedUrl(baseUrl)) {
            return@withContext ConnectionTestResult(false, "A URL deve usar HTTPS.")
        }
        val token = settings.httpToken()
        val request = Request.Builder()
            .url(baseUrl)
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", "VigiAuto/1.0 (Android)")
            .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                ConnectionTestResult(
                    ok = true,
                    detail = "Host acessível (HTTP ${response.code}). " +
                        "A validação do contrato de consulta depende dos parâmetros autorizados."
                )
            }
        }.getOrElse { error ->
            ConnectionTestResult(false, "Falha de conexão: ${error.message ?: "erro desconhecido"}")
        }
    }

    private fun buildUrl(baseUrl: String, path: String): String? {
        if (baseUrl.isBlank()) return null
        return runCatching {
            val base = baseUrl.trim().trimEnd('/')
            val suffix = path.trim()
            if (suffix.isBlank()) base
            else if (suffix.startsWith("http://") || suffix.startsWith("https://")) suffix
            else "$base/${suffix.trimStart('/')}"
        }.getOrNull()
    }

    private fun isAllowedUrl(value: String): Boolean {
        val httpUrl = value.toHttpUrlOrNull() ?: return false
        if (httpUrl.scheme == "https") return true
        return httpUrl.scheme == "http" && (httpUrl.host == "localhost" || httpUrl.host == "127.0.0.1")
    }

    private interface ConsultaPlacaApi {
        @GET
        suspend fun lookup(
            @Url url: String,
            @QueryMap parameters: Map<String, String>,
            @HeaderMap headers: Map<String, String>
        ): Response<JsonObject>
    }

    private companion object {
        const val TAG = "VigiAutoHttp"
    }
}
