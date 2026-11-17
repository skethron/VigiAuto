package br.com.vigiauto.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.vigiauto.domain.model.PhotoQuality
import br.com.vigiauto.domain.model.PlateQuality
import br.com.vigiauto.domain.model.QueryStatus
import br.com.vigiauto.domain.model.RestrictionStatus
import br.com.vigiauto.domain.model.normalizeOccurrenceDates
import br.com.vigiauto.ui.theme.Amber
import br.com.vigiauto.ui.theme.Danger
import br.com.vigiauto.ui.theme.Info
import br.com.vigiauto.ui.theme.BlueAccent
import br.com.vigiauto.ui.theme.Muted
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
private val timeShortFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())

fun formatTime(millis: Long): String = timeFormatter.format(Instant.ofEpochMilli(millis))

fun formatTimeShort(millis: Long): String = timeShortFormatter.format(Instant.ofEpochMilli(millis))

fun formatDateTime(millis: Long): String = dateTimeFormatter.format(Instant.ofEpochMilli(millis))

fun QueryStatus.label(): String = when (this) {
    QueryStatus.PENDING -> "Consulta pendente"
    QueryStatus.IN_PROGRESS -> "Consultando informações"
    QueryStatus.COMPLETED -> "Consulta concluída"
    QueryStatus.NOT_FOUND -> "Veículo não encontrado"
    QueryStatus.CONNECTION_FAILURE -> "Falha de conexão"
    QueryStatus.AUTH_ERROR -> "Erro de autenticação"
    QueryStatus.RATE_LIMITED -> "Limite de consultas atingido"
    QueryStatus.INVALID_RESPONSE -> "Resposta inválida"
    QueryStatus.INSUFFICIENT_CREDITS -> "Créditos insuficientes"
    QueryStatus.INCOMPLETE -> "Resultado incompleto"
    QueryStatus.UNAVAILABLE -> "Fonte de consulta indisponível"
}

fun queryStatusColor(status: QueryStatus): Color = when (status) {
    QueryStatus.PENDING -> Muted
    QueryStatus.IN_PROGRESS -> Info
    QueryStatus.COMPLETED -> BlueAccent
    QueryStatus.NOT_FOUND -> Amber
    QueryStatus.CONNECTION_FAILURE -> Danger
    QueryStatus.AUTH_ERROR -> Danger
    QueryStatus.RATE_LIMITED -> Amber
    QueryStatus.INVALID_RESPONSE -> Danger
    QueryStatus.INSUFFICIENT_CREDITS -> Amber
    QueryStatus.INCOMPLETE -> Amber
    QueryStatus.UNAVAILABLE -> Amber
}

fun RestrictionStatus.label(): String = when (this) {
    RestrictionStatus.CONFIRMED -> "Restrição informada pela fonte"
    RestrictionStatus.NONE_IDENTIFIED -> "Nenhuma restrição identificada na consulta"
    RestrictionStatus.INCONCLUSIVE -> "Resultado inconclusivo"
    RestrictionStatus.UNKNOWN -> "Situação desconhecida"
}

fun restrictionStatusColor(status: RestrictionStatus): Color = when (status) {
    RestrictionStatus.CONFIRMED -> Danger
    RestrictionStatus.NONE_IDENTIFIED -> BlueAccent
    RestrictionStatus.INCONCLUSIVE -> Amber
    RestrictionStatus.UNKNOWN -> Muted
}

/** Texto amigável usado no Sinesp e na Consulta Simples quando não há roubo/furto. */
const val NO_THEFT_ALERT_MESSAGE = "Veículo sem registro ou alerta de roubo/furto"

/**
 * Identifica as fontes em que a ausência de alerta de roubo/furto é exibida
 * como uma consulta concluída. O HTTP manual e o DEMO mantêm seus próprios
 * textos para não misturar contratos diferentes.
 */
fun isSinespOrSimpleSource(sourceName: String?): Boolean {
    val normalized = sourceName?.trim()?.lowercase(Locale.ROOT).orEmpty()
    if (normalized.isEmpty() || normalized.contains("http")) return false
    return normalized.contains("sinesp") ||
        normalized.contains("consulta simples") ||
        normalized.contains("consulta placa")
}

/**
 * Devolve a mensagem azul de "sem registro ou alerta" apenas quando a consulta
 * terminou sem confirmar roubo/furto. Alertas não reconhecidos ou históricos
 * indisponíveis continuam inconclusivos para não dar falsa segurança.
 */
fun noTheftAlertMessageOrNull(
    queryStatus: QueryStatus,
    restrictionStatus: RestrictionStatus,
    restrictionDescription: String?,
    sourceName: String?
): String? {
    if (queryStatus != QueryStatus.COMPLETED && queryStatus != QueryStatus.INCOMPLETE) return null
    if (restrictionStatus == RestrictionStatus.CONFIRMED) return null
    if (!isSinespOrSimpleSource(sourceName)) return null

    val description = restrictionDescription?.lowercase(Locale.ROOT).orEmpty()
    if (description.contains("reconhecid") ||
        description.contains("indisponível") ||
        description.contains("indisponivel")
    ) {
        return null
    }
    return NO_THEFT_ALERT_MESSAGE
}

/**
 * Detecta se a descrição da fonte fala especificamente de roubo, furto ou
 * extravio. A checagem ignora acentos e maiúsculas para funcionar com as
 * variações devolvidas pelas APIs.
 */
fun isTheftOrRobberyRestriction(description: String?): Boolean {
    if (description.isNullOrBlank()) return false
    val normalized = Normalizer.normalize(description, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
    return normalized.contains("roub") ||
        normalized.contains("furt") ||
        normalized.contains("extravio")
}

fun PhotoQuality.label(): String = when (this) {
    PhotoQuality.GOOD -> "Qualidade adequada"
    PhotoQuality.LIMITED -> "Qualidade limitada"
    PhotoQuality.UNAVAILABLE -> "Fotografia indisponível"
}

fun PlateQuality.label(): String = when (this) {
    PlateQuality.GOOD -> "Placa legível"
    PlateQuality.LOW -> "Placa com baixa qualidade"
    PlateQuality.UNAVAILABLE -> "Recorte indisponível"
}

@Composable
fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Status em linha para textos longos. Diferente do [StatusChip], não tenta
 * caber em uma pílula: o marcador fica fixo e o texto pode ocupar várias
 * linhas sem deformar o layout.
 */
@Composable
fun StatusLine(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, RoundedCornerShape(50))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun RestrictionAlert(
    status: RestrictionStatus,
    description: String?,
    modifier: Modifier = Modifier,
    onDarkBackground: Boolean = false
) {
    if (status != RestrictionStatus.CONFIRMED) return

    val normalizedDescription = normalizeOccurrenceDates(description)
    val theftOrRobbery = isTheftOrRobberyRestriction(normalizedDescription)
    val title = if (theftOrRobbery) "Roubo/Furto identificado" else "Restrição identificada"
    val detail = normalizedDescription?.trim()?.takeIf { it.isNotEmpty() && it != title }
        ?: "A fonte consultada informou uma restrição para este veículo."

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Danger.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, Danger.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(Danger, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "!",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(9.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    color = Danger,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = detail,
                    color = if (onDarkBackground) Color(0xFFE1E7EF)
                    else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String?) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.width(132.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value?.takeIf { it.isNotBlank() } ?: "—",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
