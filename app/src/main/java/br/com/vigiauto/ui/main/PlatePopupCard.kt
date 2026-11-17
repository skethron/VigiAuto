package br.com.vigiauto.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.vigiauto.core.AppContainer
import br.com.vigiauto.domain.model.QueryStatus
import br.com.vigiauto.domain.model.RestrictionStatus
import br.com.vigiauto.domain.model.VehicleRecord
import br.com.vigiauto.ui.components.RestrictionAlert
import br.com.vigiauto.ui.components.StatusChip
import br.com.vigiauto.ui.components.StatusLine
import br.com.vigiauto.ui.components.formatTime
import br.com.vigiauto.ui.components.label
import br.com.vigiauto.ui.components.noTheftAlertMessageOrNull
import br.com.vigiauto.ui.components.queryStatusColor
import br.com.vigiauto.ui.components.restrictionStatusColor
import br.com.vigiauto.ui.theme.Amber
import br.com.vigiauto.ui.theme.BlueAccent

@Composable
fun PlatePopupCard(
    popup: PopupItem,
    container: AppContainer,
    onOpenDetails: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var record by remember(popup.recordId) {
        mutableStateOf<VehicleRecord?>(null)
    }
    LaunchedEffect(popup.recordId) {
        container.repository.observeById(popup.recordId).collect { record = it }
    }

    val current = record
    val status = current?.queryStatus ?: QueryStatus.IN_PROGRESS
    val plate = current?.displayPlate ?: popup.displayPlate

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xF214181D),
        border = BorderStroke(1.dp, BlueAccent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = plate,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                letterSpacing = 2.sp
            )

            // O status da consulta fica em uma pílula curta. Os demais dados,
            // inclusive restrições e avisos longos, aparecem abaixo em linhas.
            StatusChip(text = status.label(), color = queryStatusColor(status))

            if (popup.isNew) {
                StatusLine(text = "Novo registro", color = BlueAccent)
            }

            val summary = current?.vehicleSummary
            Text(
                text = summary ?: when (status) {
                    QueryStatus.PENDING, QueryStatus.IN_PROGRESS -> "Consultando informações…"
                    QueryStatus.NOT_FOUND -> "Veículo não encontrado na base consultada."
                    else -> "Dados do veículo não disponíveis."
                },
                color = Color(0xFFE1E7EF),
                style = MaterialTheme.typography.bodyMedium
            )

            if (status.isTerminal || status.isFailure) {
                val restriction = current?.restrictionStatus ?: RestrictionStatus.UNKNOWN
                if (status != QueryStatus.NOT_FOUND && restriction == RestrictionStatus.CONFIRMED) {
                    RestrictionAlert(
                        status = restriction,
                        description = current?.restrictionDescription,
                        onDarkBackground = true
                    )
                } else {
                    val noTheftMessage = current?.let {
                        noTheftAlertMessageOrNull(
                            queryStatus = status,
                            restrictionStatus = it.restrictionStatus,
                            restrictionDescription = it.restrictionDescription,
                            sourceName = it.sourceName
                        )
                    }
                    val restrictionLabel = when {
                        status == QueryStatus.NOT_FOUND -> "Sem dados de restrição"
                        noTheftMessage != null -> noTheftMessage
                        else -> restriction.label()
                    }
                    StatusLine(
                        text = restrictionLabel,
                        color = when {
                            status == QueryStatus.NOT_FOUND -> Amber
                            noTheftMessage != null -> BlueAccent
                            else -> restrictionStatusColor(restriction)
                        }
                    )

                    val detail = when {
                        status == QueryStatus.NOT_FOUND ->
                            "A consulta não encontrou o veículo; isso não confirma a ausência de restrições."
                        noTheftMessage != null -> null
                        !current?.restrictionDescription.isNullOrBlank() ->
                            current?.restrictionDescription
                        restriction == RestrictionStatus.NONE_IDENTIFIED ->
                            null
                        else -> "A ausência de informação não significa ausência de restrições."
                    }
                    if (!detail.isNullOrBlank() && detail != restrictionLabel) {
                        Text(
                            text = detail,
                            color = Color(0xFFE1E7EF),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Text(
                text = "Detectada às ${formatTime(popup.detectionTimestamp)}",
                color = Color(0xFF9AA5B5),
                style = MaterialTheme.typography.labelSmall
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { current?.id?.let(onOpenDetails) },
                    enabled = current != null
                ) {
                    Text("Ver detalhes", color = BlueAccent)
                }
                TextButton(onClick = onDismiss) {
                    Text("Fechar", color = Amber)
                }
            }
        }
    }
}
