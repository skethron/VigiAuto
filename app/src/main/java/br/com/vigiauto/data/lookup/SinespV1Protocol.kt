package br.com.vigiauto.data.lookup

import br.com.vigiauto.domain.model.RestrictionStatus
import br.com.vigiauto.domain.model.VehicleData
import br.com.vigiauto.domain.model.formatOccurrenceDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.net.URI
import java.net.URLDecoder
import java.util.Base64
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Protocolo do SINESP Cidadão v1 usado pelo modo Sinesp.
 *
 * O endpoint não é uma API pública para terceiros. Ele fala o mesmo protocolo
 * do aplicativo oficial e exige:
 * - token pessoal do login gov.br no cabeçalho Authorization (token cru);
 * - assinatura anti-replay AppEnsurer;
 * - identificador estável em UniqueId;
 * - localização em Geolocation/GeolocationTimestamp/GeolocationAccuracy.
 *
 * O uso previsto é a consulta pontual de um veículo que o usuário está vendo.
 * Não há consulta em massa, varredura de placas ou tentativa de contornar
 * limites. O token é pessoal, expira e nunca deve ser compartilhado.
 */
object SinespV1Protocol {

    const val BASE_URL = "https://cidadao2.sinesp.gov.br/api/v1"
    const val AUTHORIZE_URL = "$BASE_URL/govbr/authorize"
    const val TOKEN_URL = "$BASE_URL/auth/token"
    const val DEFAULT_GEO = "-47.8825,-15.7942"
    const val DEFAULT_ACCURACY = 100
    const val SOURCE_NAME = "SINESP Cidadão (v1)"
    const val USER_AGENT = "br.gov.sinesp.cidadao.android/4.0.0"

    private const val REDIRECT_PATH = "/govbr/redirect"
    private val REDIRECT_HOSTS = setOf(
        "cidadao2.sinesp.gov.br",
        "cidadao.stage.mj.gov.br"
    )
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** O app oficial aceita a assinatura por cerca de 20 segundos. */
    const val ENSURER_VALIDITY_MS = 20_000L

    /**
     * Chave interna embutida no bundle do aplicativo oficial (v138).
     * Ela assina o AppEnsurer e muda quando o aplicativo é atualizado.
     */
    private val INTERNAL_KEY = "xjffRyW9ljxEoiFUzh2asecQs1V5JettjDoddRuJ8EFU5OBUwFp18ossI2oGsA9n".toByteArray(Charsets.UTF_8)

    private val timestampFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withZone(ZoneOffset.UTC)

    /** Monta o valor de AppEnsurer: "{epoch_ms}.{base64(HMAC-SHA256(ts, chave))}". */
    fun appEnsurer(timestampMs: Long = System.currentTimeMillis()): String {
        val timestamp = timestampMs.coerceAtLeast(0L).toString()
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(INTERNAL_KEY, "HmacSHA256"))
        val signature = Base64.getEncoder()
            .encodeToString(mac.doFinal(timestamp.toByteArray(Charsets.UTF_8)))
        return "$timestamp.$signature"
    }

    /**
     * Normaliza o token colado pelo usuário. O protocolo usa o token cru, sem
     * "Bearer"; removemos o prefixo por conveniência e quebras de linha que
     * quebrariam o cabeçalho HTTP.
     */
    fun normalizeToken(raw: String?): String? {
        val value = raw
            ?.replace("\r", "")
            ?.replace("\n", "")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        return value
            .replaceFirst(Regex("^Bearer\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    /** Valida "longitude,latitude" e devolve a forma compacta, ou null. */
    fun normalizeGeo(raw: String?): String? {
        val parts = raw?.split(',') ?: return null
        if (parts.size != 2) return null
        val longitudeText = parts[0].trim()
        val latitudeText = parts[1].trim()
        val longitude = longitudeText.toDoubleOrNull() ?: return null
        val latitude = latitudeText.toDoubleOrNull() ?: return null
        if (longitude !in -180.0..180.0 || latitude !in -90.0..90.0) return null
        return "$longitudeText,$latitudeText"
    }

    /**
     * Extrai o `code` do retorno do gov.br no WebView. Só aceita HTTPS do
     * próprio SINESP e o caminho registrado pelo aplicativo oficial; qualquer
     * outra URL é ignorada para não transformar o WebView em um coletor de
     * códigos de terceiros.
     */
    fun extractCodeFromRedirect(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (host !in REDIRECT_HOSTS) return null
        if (uri.path?.contains(REDIRECT_PATH) != true) return null
        return queryParameter(uri.rawQuery, "code")
    }

    /**
     * Aceita a URL inteira de retorno ou o `code` colado manualmente. Usado
     * apenas no fallback em que o usuário copia a URL do navegador; o WebView
     * usa [extractCodeFromRedirect], que valida host e caminho.
     */
    fun extractCode(raw: String?): String? {
        val text = raw?.trim()?.trim('"', '\'') ?: return null
        if (text.isBlank()) return null
        val fromUrl = Regex("[?&]code=([^&\\s]+)")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
        if (fromUrl != null) {
            return runCatching { URLDecoder.decode(fromUrl, "UTF-8") }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        }
        if (text.startsWith("http://", true) || text.startsWith("https://", true)) return null
        return text.takeIf { it.isNotBlank() && !it.contains(' ') }
    }

    /**
     * Se o token for um JWT, devolve o `exp` em epoch millis. Tokens opacos
     * (sem três partes) devolvem null; a validade é apenas informativa.
     */
    fun tokenExpiryEpochMs(token: String): Long? {
        val parts = token.split('.')
        if (parts.size != 3) return null
        val payload = parts[1]
        val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
        val bytes = runCatching { Base64.getUrlDecoder().decode(padded) }.getOrNull() ?: return null
        val root = runCatching {
            json.parseToJsonElement(String(bytes, Charsets.UTF_8)) as? JsonObject
        }.getOrNull() ?: return null
        val exp = (root["exp"] as? JsonPrimitive)?.content?.toLongOrNull() ?: return null
        return exp * 1_000L
    }

    private fun queryParameter(rawQuery: String?, name: String): String? {
        if (rawQuery.isNullOrBlank()) return null
        return rawQuery.split('&').firstNotNullOfOrNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) {
                null
            } else {
                val key = runCatching {
                    URLDecoder.decode(part.substring(0, separator), "UTF-8")
                }.getOrNull()
                if (key == name) {
                    runCatching {
                        URLDecoder.decode(part.substring(separator + 1), "UTF-8")
                    }.getOrNull()?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }
        }
    }

    /** Timestamp ISO-8601 em UTC com milissegundos, como o app oficial envia. */
    fun geoTimestamp(timestampMs: Long): String =
        timestampFormatter.format(Instant.ofEpochMilli(timestampMs))

    /** Cabeçalhos completos de uma consulta, sem expor o token em outros lugares. */
    fun headers(
        token: String,
        uniqueId: String,
        geo: String,
        timestampMs: Long
    ): Map<String, String> {
        val safeToken = normalizeToken(token) ?: ""
        return mapOf(
            "Accept" to "application/json",
            "User-Agent" to USER_AGENT,
            "Authorization" to safeToken,
            "AppEnsurer" to appEnsurer(timestampMs),
            "UniqueId" to uniqueId,
            "Geolocation" to (normalizeGeo(geo) ?: DEFAULT_GEO),
            "GeolocationTimestamp" to geoTimestamp(timestampMs),
            "GeolocationAccuracy" to DEFAULT_ACCURACY.toString()
        )
    }

    /**
     * Converte o JSON do `GET /vehicles/{placa}` em [VehicleData].
     *
     * Regras de situação, espelhando o aplicativo oficial:
     * - `alert` com ROUBADO/FURTADO: restrição confirmada;
     * - `alert` com outro tipo: inconclusivo (nunca "sem restrição");
     * - `alert` vazio: sem registro/alerta de roubo/furto;
     * - `alert` ausente ou malformado: inconclusivo, porque a ausência do campo
     *   não é prova de regularidade.
     */
    fun mapResponse(root: JsonObject): VehicleData? {
        val data = if (!root.containsKey("plate") && root["data"] is JsonObject) {
            root["data"] as JsonObject
        } else {
            root
        }

        val modelText = data.text("model")
        val color = data.text("color")
        val manufactureYear = yearOf(data.text("fabricationYear"))
        val modelYear = yearOf(data.text("year"))
        val category = data.text("species")
            ?: data.text("vehicleType")
            ?: data.text("category")
            ?: data.text("categoria")
        val city = data.text("city")
            ?: data.text("municipio")
            ?: data.text("municipality")
        val state = data.text("state")
            ?: data.text("uf")
            ?: data.text("estado")
        val cortexValidation = data.boolean("hasCortexValidation")

        val recognizedAnyData = data.containsKey("plate") ||
            modelText != null ||
            data.containsKey("chassis") ||
            data.containsKey("alert")
        if (!recognizedAnyData) return null

        val (restrictionStatus, restrictionDescription) = interpretAlert(
            element = data["alert"],
            hasCortexValidation = cortexValidation
        )

        val (brand, model) = splitModel(modelText)
        val incomplete = (brand == null && model == null) ||
            (color == null && manufactureYear == null && modelYear == null && category == null)

        return VehicleData(
            brand = brand,
            model = model,
            version = null,
            color = color,
            manufactureYear = manufactureYear,
            modelYear = modelYear,
            vehicleCategory = category,
            city = city,
            state = state,
            restrictionStatus = restrictionStatus,
            restrictionDescription = restrictionDescription,
            sourceName = SOURCE_NAME,
            incomplete = incomplete
        )
    }

    private fun interpretAlert(
        element: JsonElement?,
        hasCortexValidation: Boolean?
    ): Pair<RestrictionStatus, String?> {
        if (element is JsonArray && element.isEmpty()) {
            val suffix = if (hasCortexValidation == false) " - SENATRAN" else ""
            return RestrictionStatus.NONE_IDENTIFIED to
                "Sem registro ou alerta de roubo/furto$suffix"
        }

        val alert = when (element) {
            is JsonArray -> element.firstOrNull()
            is JsonObject -> element
            else -> null
        }

        if (alert == null) {
            return RestrictionStatus.INCONCLUSIVE to
                "A fonte não informou a situação de roubo/furto."
        }

        val alertObject = alert as? JsonObject
            ?: return RestrictionStatus.INCONCLUSIVE to
                "A situação de roubo/furto não foi reconhecida."

        val type = alertObject.text("type")?.uppercase(Locale.ROOT).orEmpty()
        if (type == "ROUBADO" || type == "FURTADO") {
            // No alerta positivo, o app oficial só dispensa o sufixo quando
            // hasCortexValidation é exatamente verdadeiro.
            val suffix = if (hasCortexValidation == true) "" else " - SENATRAN"
            val occurrence = formatOccurrenceDate(alertObject.text("occurrenceDate"))
            val detail = buildString {
                append("Veículo roubado/furtado")
                append(suffix)
                if (occurrence != null) append(" (Ocorrência: $occurrence)")
            }
            return RestrictionStatus.CONFIRMED to detail
        }

        val detail = if (type.isEmpty()) {
            "Situação não reconhecida pela fonte."
        } else {
            "Situação não reconhecida: $type"
        }
        return RestrictionStatus.INCONCLUSIVE to detail
    }

    private fun splitModel(value: String?): Pair<String?, String?> {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null to null
        val slash = text.indexOf('/')
        if (slash <= 0 || slash == text.lastIndex) return null to text
        val brand = text.substring(0, slash).trim().takeIf { it.isNotEmpty() }
        val model = text.substring(slash + 1).trim().takeIf { it.isNotEmpty() }
        return if (brand != null && model != null) brand to model else null to text
    }

    private fun yearOf(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        return Regex("(19|20)\\d{2}").find(value)?.value?.toIntOrNull()
            ?: value.filter { it.isDigit() }.take(4).toIntOrNull()?.takeIf { it in 1900..2100 }
    }

    private fun JsonObject.text(key: String): String? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        val content = primitive.content.trim()
        return content.takeIf { it.isNotEmpty() && it != "null" }
    }

    private fun JsonObject.boolean(key: String): Boolean? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        return when (primitive.content.trim().lowercase(Locale.ROOT)) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }
}
