package br.com.vigiauto.data.lookup

import br.com.vigiauto.domain.model.RestrictionStatus
import br.com.vigiauto.domain.model.VehicleData
import br.com.vigiauto.domain.model.normalizeOccurrenceDates
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.Normalizer
import java.util.Locale

/**
 * Mapeador tolerante para o JSON do serviço Consulta Placa.
 *
 * O contrato observado em 19/10/2026 traz os dados principais na raiz
 * (placa, marca, modelo, ano, anoModelo, cor, situacao) e detalhes em `extra`
 * (restricao_1..4, tipo_veiculo, especie_veiculo etc.). O mapeador também
 * aceita variações de nome e sinaliza resultado incompleto quando só parte
 * dos dados é reconhecida. Nunca inventa valores ausentes.
 */
object VehicleResponseMapper {

    private val negativeRestrictionPhrases = listOf(
        "nada consta", "sem restri", "nenhuma restri", "sem alerta", "sem ocorrencia",
        "regular", "normal", "liberado", "apto", "ok"
    )

    /**
     * Valores usados por APIs que representam a restrição com um booleano ou
     * com "sim"/"não" (por exemplo, possui_registro: "sim"). Só são aceitos
     * quando a chave também indica uma restrição, para não transformar um
     * "status": "sim" genérico em roubo/furto.
     */
    private val positiveFlagValues = setOf("sim", "true", "1", "s", "positivo")
    private val negativeFlagValues = setOf("nao", "false", "0", "n", "negativo")

    private val positiveRestrictionWords = listOf(
        // "roub" e "furt" cobrem roubo, roubado, roubada, furto, furtado e
        // furtada. A API pode devolver qualquer uma dessas flexões.
        "roub", "furt", "extravio", "restri", "bloqueio", "sinistro", "leilao",
        "apreensao", "baixa", "pendencia", "irregular", "alerta", "judicial",
        "administrativa"
    )

    private val restrictionKeys = listOf(
        "restri", "restricao", "restricoes", "alerta", "alertas", "roub", "furt",
        "bloqueio", "sinistro", "leilao", "situacao", "status", "possui_registro",
        "ocorrencia", "ocorrencias"
    )

    fun map(root: JsonObject, defaultSource: String = "Consulta Simples (API)"): VehicleData? {
        if (root.isEmpty()) return null

        val fields = LinkedHashMap<String, String>()
        flatten(root, fields)
        if (fields.isEmpty()) return null

        fun text(vararg aliases: String): String? =
            aliases.firstNotNullOfOrNull { alias ->
                fields[normalize(alias)]?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
            }

        val brand = text("marca", "fabricante", "brand", "make")
        val model = text("modelo", "model", "modeloVeiculo")
        val version = text("versao", "versaoModelo", "version", "trim")
        val color = text("cor", "color")
        val manufactureYear = yearOf(text("anoFabricacao", "anoFabricacaoModelo", "anoFab", "manufactureYear", "ano"))
        val modelYear = yearOf(text("anoModelo", "anoModeloVeiculo", "modelYear", "anoModel"))
        val category = text(
            "tipoVeiculo", "especieVeiculo", "categoria", "tipo", "category", "especie"
        )
        val city = text("municipio", "municipality", "cidade", "city", "localidade")
        val state = text("uf", "estado", "state", "province")
        val declaredSource = text("fonte", "source", "origem")

        val restriction = detectRestriction(fields)

        val recognizedAnyData = listOf(
            brand, model, version, color, category, declaredSource
        ).any { it != null } || manufactureYear != null || modelYear != null ||
            restriction.status != RestrictionStatus.UNKNOWN

        if (!recognizedAnyData) return null

        val incomplete = (brand == null && model == null) ||
            (color == null && manufactureYear == null && modelYear == null && category == null)

        return VehicleData(
            brand = brand,
            model = model,
            version = version,
            color = color,
            manufactureYear = manufactureYear,
            modelYear = modelYear,
            vehicleCategory = category,
            city = city,
            state = state,
            restrictionStatus = restriction.status,
            restrictionDescription = normalizeOccurrenceDates(restriction.description),
            sourceName = declaredSource ?: defaultSource,
            incomplete = incomplete
        )
    }

    private data class RestrictionDetection(val status: RestrictionStatus, val description: String?)

    private fun detectRestriction(fields: Map<String, String>): RestrictionDetection {
        val relatedEntries = fields.entries
            .filter { (key, _) -> restrictionKeys.any { key.contains(normalize(it)) } }
            .filter { it.value.isNotBlank() }

        if (relatedEntries.isEmpty()) return RestrictionDetection(RestrictionStatus.UNKNOWN, null)

        val positive = mutableListOf<String>()
        val negative = mutableListOf<String>()
        val inconclusive = mutableListOf<String>()

        for ((key, value) in relatedEntries) {
            val normalizedKey = normalize(key)
            val normalizedValue = normalize(value)
            if (normalizedValue.isEmpty()) continue
            if (isGenericEnvelopeStatus(normalizedKey, normalizedValue)) continue

            when {
                isNegativeRestriction(normalizedValue) -> negative += value
                isPositiveRestriction(normalizedKey, normalizedValue) ->
                    positive += positiveDescription(key, value, normalizedKey, normalizedValue)
                else -> inconclusive += value
            }
        }

        // Uma restrição positiva tem precedência sobre um "sem restrição"
        // genérico, mas "SEM RESTRICAO" nunca pode ser classificado como
        // positiva só porque contém a palavra "restri".
        if (positive.isNotEmpty()) {
            val mostSpecific = positive.maxByOrNull { it.length } ?: positive.first()
            return RestrictionDetection(RestrictionStatus.CONFIRMED, mostSpecific)
        }

        if (negative.isNotEmpty()) {
            // Evita usar o "ok" genérico de um envelope de API como descrição
            // quando existe um "não" mais informativo ao lado.
            val mostSpecific = negative
                .filterNot { normalize(it) == "ok" }
                .maxByOrNull { it.length }
                ?: negative.first()
            return RestrictionDetection(RestrictionStatus.NONE_IDENTIFIED, mostSpecific)
        }

        return RestrictionDetection(RestrictionStatus.INCONCLUSIVE, inconclusive.firstOrNull())
    }

    private fun isNegativeRestriction(normalizedValue: String): Boolean =
        normalizedValue in negativeFlagValues ||
            negativeRestrictionPhrases.any { normalizedValue.contains(normalize(it)) }

    /**
     * "status": "ok" costuma ser o envelope da resposta, não a situação do
     * veículo. Ignorá-lo evita transformar uma consulta bem-sucedida em
     * "nenhuma restrição" sem que a fonte tenha dito isso.
     */
    private fun isGenericEnvelopeStatus(normalizedKey: String, normalizedValue: String): Boolean =
        (normalizedKey == "status" || normalizedKey == "situacao") &&
            (normalizedValue == "ok" || normalizedValue == "success" || normalizedValue == "sucesso")

    private fun isPositiveRestriction(normalizedKey: String, normalizedValue: String): Boolean {
        if (positiveRestrictionWords.any { normalizedValue.contains(normalize(it)) }) return true

        if (normalizedValue in positiveFlagValues) {
            return restrictionKeys.any { candidate ->
                val part = normalize(candidate)
                part != "status" && part != "situacao" && normalizedKey.contains(part)
            }
        }
        return false
    }

    private fun positiveDescription(
        key: String,
        value: String,
        normalizedKey: String,
        normalizedValue: String
    ): String {
        if (positiveRestrictionWords.any { normalizedValue.contains(normalize(it)) }) {
            return value
        }
        return when {
            normalizedKey.contains("roub") || normalizedKey.contains("furt") ->
                "Roubo/Furto informado pela fonte"
            normalizedKey.contains("possuiregistro") ->
                "Registro de roubo/furto informado pela fonte"
            else -> value
        }
    }

    private fun yearOf(value: String?): Int? {
        if (value == null) return null
        return Regex("(19|20)\\d{2}").find(value)?.value?.toIntOrNull()
            ?: value.filter { it.isDigit() }.take(4).toIntOrNull()?.takeIf { it in 1900..2100 }
    }

    private fun flatten(element: JsonElement, output: MutableMap<String, String>, prefix: String = "") {
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                val normalizedKey = normalize(key)
                if (value is JsonPrimitive && (value.isString || value.content != "null")) {
                    if (normalizedKey.isNotEmpty()) output.putIfAbsent(normalizedKey, value.content)
                } else {
                    flatten(value, output, normalizedKey.ifEmpty { prefix })
                }
            }
            is JsonArray -> element.forEach { flatten(it, output, prefix) }
            is JsonPrimitive -> if (element.isString && prefix.isNotEmpty()) {
                output.putIfAbsent(prefix, element.content)
            }
            else -> Unit
        }
    }

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .filter { it in 'a'..'z' || it in '0'..'9' }
    }
}
