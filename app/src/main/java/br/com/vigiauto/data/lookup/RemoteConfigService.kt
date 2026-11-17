package br.com.vigiauto.data.lookup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Obtém a URL/token atuais do remote config público do serviço.
 *
 * Segurança:
 * - a configuração é servida por HTTPS;
 * - o token fica apenas em memória e não é gravado no APK;
 * - nenhuma placa é enviada ao buscar a configuração;
 * - o cache expira em 1 hora e pode ser invalidado em caso de 401/403.
 */
class RemoteConfigService(
    private val client: OkHttpClient = defaultClient(),
    private val configUrl: String = DEFAULT_CONFIG_URL,
    private val clock: () -> Long = System::currentTimeMillis
) {

    private val mutex = Mutex()
    private var cached: CachedConfig? = null

    private data class CachedConfig(val apiTemplate: String, val fetchedAt: Long)

    suspend fun apiTemplate(forceRefresh: Boolean = false): String? = mutex.withLock {
        val now = clock()
        val current = cached
        if (!forceRefresh && current != null && now - current.fetchedAt < CACHE_TTL_MS) {
            return current.apiTemplate
        }

        val fetched = fetchTemplate()
        if (fetched != null) {
            cached = CachedConfig(fetched, now)
            fetched
        } else {
            // Em falha temporária, mantém a última configuração conhecida.
            current?.apiTemplate
        }
    }

    fun invalidate() {
        cached = null
    }

    suspend fun warmUp() {
        runCatching { apiTemplate() }
    }

    private suspend fun fetchTemplate(): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(configUrl)
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                RemoteApiConfig.parseApiTemplate(body)
            }
        } catch (_: Exception) {
            // Sem log do corpo/URL: a URL pode conter o token.
            null
        }
    }

    companion object {
        const val DEFAULT_CONFIG_URL = "https://costaw.com.br/remote-config/consultaplaca.json"
        private const val USER_AGENT = "VigiAuto/1.1 (Android)"
        private const val CACHE_TTL_MS = 60 * 60 * 1000L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
    }
}
