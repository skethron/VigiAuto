package br.com.vigiauto.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.vigiauto.core.AppContainer
import br.com.vigiauto.domain.model.QueryStatus
import br.com.vigiauto.domain.model.RestrictionStatus
import br.com.vigiauto.domain.model.VehicleRecord
import br.com.vigiauto.ui.StatusBarAppearance
import br.com.vigiauto.ui.components.RestrictionAlert
import br.com.vigiauto.ui.components.StatusChip
import br.com.vigiauto.ui.components.StatusLine
import br.com.vigiauto.ui.components.formatDateTime
import br.com.vigiauto.ui.components.formatTimeShort
import br.com.vigiauto.ui.components.label
import br.com.vigiauto.ui.components.noTheftAlertMessageOrNull
import br.com.vigiauto.ui.components.queryStatusColor
import br.com.vigiauto.ui.components.restrictionStatusColor
import br.com.vigiauto.ui.theme.BlueAccent
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenDetails: (Long) -> Unit
) {
    val viewModel: HistoryViewModel = viewModel()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()

    var recordToDelete by remember { mutableStateOf<VehicleRecord?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    StatusBarAppearance()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Histórico de veículos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    TextButton(onClick = { showClearAllDialog = true }) {
                        Text("Limpar")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp)
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = viewModel::setSearch,
                label = { Text("Pesquisar placa, marca ou modelo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HistoryFilter.entries.forEach { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { viewModel.setFilter(option) },
                            label = { Text(option.label) }
                        )
                    }
                }
                Box {
                    IconButton(onClick = { sortMenuExpanded = true }) {
                        Icon(Icons.Filled.Sort, contentDescription = "Ordenar")
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        HistorySort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    viewModel.setSort(option)
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (records.isEmpty()) {
                EmptyHistory()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
                ) {
                    items(records, key = { it.id }) { record ->
                        HistoryItem(
                            record = record,
                            container = container,
                            onClick = { onOpenDetails(record.id) },
                            onDelete = { recordToDelete = record }
                        )
                    }
                }
            }
        }
    }

    recordToDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("Apagar registro?") },
            text = {
                Text("O registro ${record.displayPlate} e a fotografia associada serão removidos permanentemente.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecord(record.id)
                    recordToDelete = null
                }) { Text("Apagar") }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) { Text("Cancelar") }
            }
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Limpar todo o histórico?") },
            text = { Text("Todos os registros e fotografias serão apagados. Esta ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAll()
                    showClearAllDialog = false
                }) { Text("Limpar tudo") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun HistoryItem(
    record: VehicleRecord,
    container: AppContainer,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val photo = remember(record.photoPath) { container.photoStorage.resolve(record.photoPath) }
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (photo != null) {
                    AsyncImage(
                        model = photo,
                        contentDescription = "Fotografia de ${record.displayPlate}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Filled.Image,
                        contentDescription = "Sem fotografia",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = record.displayPlate,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (record.occurrenceCount > 1) {
                        StatusChip(
                            text = "${record.occurrenceCount}x",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = record.vehicleSummary ?: "Dados do veículo indisponíveis",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                StatusChip(
                    text = record.queryStatus.label(),
                    color = queryStatusColor(record.queryStatus)
                )
                if (record.queryStatus.isTerminal) {
                    if (record.restrictionStatus == RestrictionStatus.CONFIRMED) {
                        RestrictionAlert(
                            status = record.restrictionStatus,
                            description = record.restrictionDescription
                        )
                    } else {
                        val noTheftMessage = noTheftAlertMessageOrNull(
                            queryStatus = record.queryStatus,
                            restrictionStatus = record.restrictionStatus,
                            restrictionDescription = record.restrictionDescription,
                            sourceName = record.sourceName
                        )
                        StatusLine(
                            text = noTheftMessage ?: record.restrictionStatus.label(),
                            color = if (noTheftMessage != null) {
                                BlueAccent
                            } else {
                                restrictionStatusColor(record.restrictionStatus)
                            }
                        )
                    }
                }

                Text(
                    text = "1ª: ${formatDateTime(record.firstDetectionTimestamp)} • " +
                        "Última: ${formatTimeShort(record.lastSeenTimestamp)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                record.locationSummary?.let { location ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = location,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Apagar registro")
            }
        }
    }
}

@Composable
private fun EmptyHistory() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Image,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Nenhuma placa registrada ainda",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Aponte a câmera para uma placa para criar o primeiro registro.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
