package br.com.vigiauto.data.lookup

import br.com.vigiauto.domain.model.NmtStatus
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.text.Normalizer
import java.util.Locale

/**
 * Resultado bruto e normalizado de uma página de consulta OTTC/NMT.
 *
 * [rawMessage] é mantido apenas em memória e nunca é gravado no banco. O
 * worker persiste somente [rawStatus] quando o texto é reconhecido.
 */
data class NmtPageResult(
    val status: NmtStatus,
    val rawStatus: String? = null,
    val rawMessage: String? = null,
    val errorCode: String? = null
)

/**
 * Parser puro do HTML devolvido pelo site da Prefeitura de São Paulo.
 *
 * O site usa ASP.NET WebForms e devolve a página inteira; o resultado fica em
 * `#retornoMsg` e o erro em `#errorMsg`. A função não faz I/O, não abre
 * navegador e pode ser testada com fixtures.
 */
object NmtPageParser {

    fun parse(html: String?): NmtPageResult {
        if (html.isNullOrBlank()) return NmtPageResult(NmtStatus.UNKNOWN, errorCode = "empty_html")

        val document = runCatching { Jsoup.parse(html) }.getOrNull()
            ?: return NmtPageResult(NmtStatus.UNKNOWN, errorCode = "invalid_html")

        val error = document.selectFirst("#errorMsg")?.takeIf(::isVisible)
        if (error != null) {
            val message = normalizeText(error.text())
            return if (fold(message).contains("PLACA INVALIDA")) {
                NmtPageResult(
                    status = NmtStatus.INVALID_PLATE,
                    rawStatus = message,
                    rawMessage = message,
                    errorCode = "invalid_plate"
                )
            } else {
                NmtPageResult(
                    status = NmtStatus.UNKNOWN,
                    rawStatus = message,
                    rawMessage = message,
                    errorCode = "error_msg"
                )
            }
        }

        val result = document.selectFirst("#retornoMsg")?.takeIf(::isVisible)
            ?: return NmtPageResult(NmtStatus.UNKNOWN, errorCode = "no_result")

        val message = normalizeText(result.text())
        val rawStatus = message
            .substringAfterLast("Veículo", message)
            .trim()
            .ifBlank { message }
        val normalized = fold(rawStatus)

        val status = when {
            normalized.contains("NAO CADASTRAD") -> NmtStatus.NOT_REGISTERED
            normalized == "IRREGULAR" -> NmtStatus.IRREGULAR
            normalized == "REGULAR" -> NmtStatus.REGULAR
            else -> NmtStatus.UNKNOWN
        }

        return NmtPageResult(
            status = status,
            rawStatus = rawStatus,
            rawMessage = message,
            errorCode = if (status == NmtStatus.UNKNOWN) "unknown_status" else null
        )
    }

    /**
     * O HTML inicial mantém os dois blocos no DOM, ocultos por CSS inline. A
     * verificação por estilo é suficiente porque o resultado é extraído do
     * bloco que o próprio site acabou de exibir.
     */
    private fun isVisible(element: Element): Boolean {
        if (element.hasAttr("hidden")) return false
        val style = element.attr("style")
            .replace(" ", "")
            .replace("\t", "")
            .lowercase(Locale.ROOT)
        if (style.contains("display:none") || style.contains("visibility:hidden")) return false
        return element.text().isNotBlank()
    }

    private fun normalizeText(value: String): String =
        value.replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun fold(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .uppercase(Locale.ROOT)
            .trim()
}
