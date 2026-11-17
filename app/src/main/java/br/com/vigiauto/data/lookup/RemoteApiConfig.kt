package br.com.vigiauto.data.lookup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Configuração pública da API, lida do remote config do próprio serviço.
 *
 * O token nunca é embutido no APK: ele chega junto com a URL no remote config
 * em tempo de execução. Se o provedor rotacionar o token, a próxima consulta
 * busca a configuração novamente.
 */
object RemoteApiConfig {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parseApiTemplate(body: String): String? = runCatching {
        val root = json.parseToJsonElement(body) as? JsonObject ?: return null
        val value = (root["apiPlaca"] as? JsonPrimitive)?.content?.trim() ?: return null
        value.takeIf { isValidTemplate(it) }
    }.getOrNull()

    fun isValidTemplate(template: String): Boolean {
        val url = template.toHttpUrlOrNull() ?: return false
        return url.scheme == "https"
    }

    /**
     * Troca o marcador "placaaqui" (ou substitui o parâmetro `placa`) pela
     * placa normalizada. Preserva o token e quaisquer outros parâmetros.
     */
    fun forPlate(template: String, plate: String): String? {
        val url = template.toHttpUrlOrNull() ?: return null
        if (url.scheme != "https") return null
        return url.newBuilder()
            .setQueryParameter("placa", plate)
            .build()
            .toString()
    }

    fun hostOf(template: String): String? = template.toHttpUrlOrNull()?.host

    fun messageFrom(body: String?): String? = runCatching {
        if (body.isNullOrBlank()) return null
        val root = json.parseToJsonElement(body) as? JsonObject ?: return null
        root["message"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()
}
