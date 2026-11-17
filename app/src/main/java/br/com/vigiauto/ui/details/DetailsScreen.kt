package br.com.vigiauto.ui.details

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.vigiauto.core.AppContainer
import br.com.vigiauto.domain.model.PhotoQuality
import br.com.vigiauto.domain.model.PlateQuality
import br.com.vigiauto.domain.model.QueryStatus
import br.com.vigiauto.ui.StatusBarAppearance
import br.com.vigiauto.ui.components.InfoRow
import br.com.vigiauto.ui.components.SectionCard
import br.com.vigiauto.ui.components.RestrictionAlert
import br.com.vigiauto.ui.components.StatusChip
import br.com.vigiauto.ui.components.StatusLine
import br.com.vigiauto.ui.components.formatDateTime
import br.com.vigiauto.ui.components.label
import br.com.vigiauto.ui.components.noTheftAlertMessageOrNull
import br.com.vigiauto.ui.components.queryStatusColor
import br.com.vigiauto.ui.components.restrictionStatusColor
import br.com.vigiauto.ui.theme.BlueAccent
import coil.compose.AsyncImage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    container: AppContainer,
    recordId: Long,
    onBack: () -> Unit
) {
    val viewModel: DetailsViewModel = viewModel()
    val context = LocalContext.current
    val record by container.repository.observeById(recordId)
        .collectAsStateWithLifecycle(initialValue = null)
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val sharePayload by viewModel.sharePayload.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(notice) {
        notice?.let { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(sharePayload) {
        val payload = sharePayload ?: return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, payload.uri)
            putExtra(Intent.EXTRA_SUBJECT, payload.subject)
            clipData = ClipData.newRawUri("VigiAuto", payload.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            val chooser = Intent.createChooser(send, "Compartilhar foto").apply {
                // Alguns pacotes só recebem a permissão de leitura se ela também
                // estiver no chooser, não apenas no Intent de envio.
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
        }.onFailure {
            viewModel.showNotice("Não foi possível abrir o compartilhamento.")
        }
        viewModel.consumeSharePayload()
    }

    var showZoom by remember { mutableStateOf(false) }
    var showPlateZoom by remember { mutableStateOf(false) }
    var showReplacePhotoDialog by remember { mutableStateOf(false) }
    var showRemovePhotoDialog by remember { mutableStateOf(false) }
    var showDeleteRecordDialog by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.attachPhotoFromGallery(recordId, uri)
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.downloadPhoto(recordId)
        } else {
            viewModel.showNotice("Permita o armazenamento para baixar a fotografia.")
        }
    }

    StatusBarAppearance()

    val current = record

    val photoFile: File? = remember(current?.photoPath) {
        container.photoStorage.resolve(current?.photoPath)
    }
    val plateCropFile: File? = remember(current?.plateCropPath) {
        container.photoStorage.resolve(current?.plateCropPath)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.displayPlate ?: "Veículo") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.shareRecord(recordId) },
                        enabled = current != null
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Compartilhar foto")
                    }
                    IconButton(onClick = { viewModel.requery(recordId) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Consultar novamente")
                    }
                    IconButton(onClick = { showDeleteRecordDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Apagar registro")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (current == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Registro não encontrado.")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCard(title = "A · Fotografia do veículo") {
                val warningColor = Color(0xFFF9A825)
                val plateAspect = current.plateCropAspectRatio
                    ?.takeIf { it in 0.5f..6f }
                    ?: 2.2f
                val plateColor = when (current.plateQualityStatus) {
                    PlateQuality.GOOD -> MaterialTheme.colorScheme.primary
                    PlateQuality.LOW -> warningColor
                    PlateQuality.UNAVAILABLE -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = photoFile != null) { showZoom = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (photoFile != null) {
                        AsyncImage(
                            model = photoFile,
                            contentDescription = "Fotografia de ${current.displayPlate}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (plateCropFile != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(10.dp)
                                    .fillMaxWidth(0.38f)
                                    .aspectRatio(plateAspect)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.35f))
                                    .border(1.5.dp, BlueAccent, RoundedCornerShape(6.dp))
                                    .clickable { showPlateZoom = true }
                            ) {
                                AsyncImage(
                                    model = plateCropFile,
                                    contentDescription = "Recorte da placa ${current.displayPlate}",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (current.plateNeedsReview) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .background(Color.Black.copy(alpha = 0.65f))
                                            .padding(2.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Warning,
                                            contentDescription = null,
                                            tint = Color(0xFFFFD54F),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Fotografia indisponível",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Text(
                    text = "Captura: ${current.photoCaptureTimestamp?.let { formatDateTime(it) } ?: "—"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(
                        text = current.photoQualityStatus.label(),
                        color = when {
                            current.photoPath == null -> MaterialTheme.colorScheme.onSurfaceVariant
                            current.photoQualityStatus == PhotoQuality.LIMITED -> warningColor
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                    if (current.plateCropPath != null) {
                        StatusChip(
                            text = current.plateQualityStatus.label(),
                            color = plateColor
                        )
                    }
                }
                if (current.plateNeedsReview && current.photoPath != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = warningColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Verificação recomendada: a leitura ou a imagem tem baixa " +
                                "qualidade. O recorte original foi preservado, sem reconstrução.",
                            style = MaterialTheme.typography.labelSmall,
                            color = warningColor
                        )
                    }
                }
                current.plateOcrText?.takeIf { it.isNotBlank() }?.let {
                    InfoRow("Texto lido pelo OCR", it)
                }
                current.plateDetectorConfidence?.let {
                    InfoRow("Confiança da detecção", "%.0f%%".format(it * 100f))
                }
                current.plateOcrConfidence?.let {
                    InfoRow("Confiança do OCR", "%.0f%%".format(it * 100f))
                }
                if (plateCropFile != null) {
                    Text(
                        text = "A miniatura azul é o recorte real da placa. Toque nela para ampliar.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (photoFile != null) {
                    Text(
                        text = "Esta captura é anterior ao recorte automático da placa. " +
                            "As próximas leituras confirmadas já terão a miniatura.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (photoFile != null) {
                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    storagePermissionLauncher.launch(
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    )
                                } else {
                                    viewModel.downloadPhoto(recordId)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null)
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Baixar foto", maxLines = 1, softWrap = false)
                        }
                        OutlinedButton(onClick = { showRemovePhotoDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Remover", maxLines = 1, softWrap = false)
                        }
                    } else {
                        OutlinedButton(onClick = { showReplacePhotoDialog = true }) {
                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Adicionar foto")
                        }
                    }
                }
                if (photoFile != null) {
                    Text(
                        text = "A fotografia original é preservada nas releituras. Para trocá-la, remova a atual primeiro.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionCard(title = "B · Identificação do veículo") {
                InfoRow("Placa", current.displayPlate)
                InfoRow("Marca", current.brand)
                InfoRow("Modelo", current.model)
                InfoRow("Cor", current.color)
                InfoRow("Ano fabricação", current.manufactureYear?.toString())
                InfoRow("Ano modelo", current.modelYear?.toString())
                val category = current.nmtStatus?.categoryLabel()
                    ?: current.vehicleCategory?.takeIf { it.isNotBlank() }
                InfoRow("Categoria", category)
                InfoRow("Cidade", current.city)
                InfoRow("Estado", current.state)
                if (!current.hasVehicleData) {
                    Text(
                        text = if (current.queryStatus == QueryStatus.NOT_FOUND) {
                            "O serviço não encontrou este veículo na base consultada."
                        } else {
                            "A fonte consultada ainda não retornou dados de identificação."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionCard(title = "C · Situação e restrições") {
                val noTheftMessage = noTheftAlertMessageOrNull(
                    queryStatus = current.queryStatus,
                    restrictionStatus = current.restrictionStatus,
                    restrictionDescription = current.restrictionDescription,
                    sourceName = current.sourceName
                )
                StatusChip(
                    text = current.queryStatus.label(),
                    color = queryStatusColor(current.queryStatus)
                )
                if (current.queryStatus.isTerminal || current.queryStatus.isFailure) {
                    if (current.restrictionStatus == br.com.vigiauto.domain.model.RestrictionStatus.CONFIRMED) {
                        RestrictionAlert(
                            status = current.restrictionStatus,
                            description = current.restrictionDescription
                        )
                    } else {
                        StatusLine(
                            text = noTheftMessage ?: current.restrictionStatus.label(),
                            color = if (noTheftMessage != null) {
                                BlueAccent
                            } else {
                                restrictionStatusColor(current.restrictionStatus)
                            }
                        )
                        InfoRow(
                            "Descrição",
                            noTheftMessage ?: current.restrictionDescription
                        )
                    }
                } else {
                    InfoRow("Descrição", current.restrictionDescription)
                }
                InfoRow("Fonte", current.sourceName)
                InfoRow("Consulta", current.queryTimestamp?.let { formatDateTime(it) })
                if (current.queryStatus == QueryStatus.NOT_FOUND) {
                    Text(
                        text = "A consulta não retornou dados para esta placa. Isso não permite concluir que o veículo não possui restrições.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (current.restrictionStatus == br.com.vigiauto.domain.model.RestrictionStatus.UNKNOWN &&
                    noTheftMessage == null
                ) {
                    Text(
                        text = "A ausência de informação não significa ausência de restrições.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (current.queryStatus.isFailure || current.queryStatus == QueryStatus.PENDING ||
                    current.queryStatus == QueryStatus.NOT_FOUND
                ) {
                    OutlinedButton(onClick = { viewModel.requery(recordId) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Tentar consulta novamente")
                    }
                }
            }

            SectionCard(title = "D · Histórico de identificação") {
                InfoRow("Primeira identificação", formatDateTime(current.firstDetectionTimestamp))
                InfoRow("Última visualização", formatDateTime(current.lastSeenTimestamp))
                InfoRow("Ocorrências reconhecidas", current.occurrenceCount.toString())
                InfoRow(
                    "Confiança máxima",
                    "%.0f%%".format(current.recognitionConfidence * 100f)
                )
                InfoRow("Última atualização dos dados", current.queryTimestamp?.let { formatDateTime(it) })
            }

            SectionCard(title = "E · Localização da consulta") {
                if (current.hasLocation) {
                    Text(
                        text = current.locationFullLine ?: "—",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = "A localização do aparelho não foi capturada nesta consulta. " +
                            "Verifique a permissão de localização do VigiAuto.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showZoom && photoFile != null) {
        PhotoZoomDialog(file = photoFile, onDismiss = { showZoom = false })
    }

    if (showPlateZoom && plateCropFile != null) {
        PhotoZoomDialog(
            file = plateCropFile,
            contentDescription = "Recorte ampliado da placa",
            onDismiss = { showPlateZoom = false }
        )
    }

    if (showReplacePhotoDialog) {
        AlertDialog(
            onDismissRequest = { showReplacePhotoDialog = false },
            title = { Text("Adicionar fotografia") },
            text = { Text("Escolha uma imagem da galeria para associar a este veículo.") },
            confirmButton = {
                TextButton(onClick = {
                    showReplacePhotoDialog = false
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }) { Text("Escolher imagem") }
            },
            dismissButton = {
                TextButton(onClick = { showReplacePhotoDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showRemovePhotoDialog) {
        AlertDialog(
            onDismissRequest = { showRemovePhotoDialog = false },
            title = { Text("Remover fotografia?") },
            text = { Text("A fotografia será apagada do armazenamento do aplicativo.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removePhoto(recordId)
                    showRemovePhotoDialog = false
                }) { Text("Remover") }
            },
            dismissButton = {
                TextButton(onClick = { showRemovePhotoDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showDeleteRecordDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteRecordDialog = false },
            title = { Text("Apagar registro?") },
            text = { Text("O registro e a fotografia serão removidos permanentemente.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteRecordDialog = false
                    viewModel.deleteRecord(recordId)
                    onBack()
                }) { Text("Apagar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteRecordDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun PhotoZoomDialog(
    file: File,
    contentDescription: String = "Fotografia ampliada",
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        offset += pan
                    }
                }
        ) {
            AsyncImage(
                model = file,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = Color.White)
            }
        }
    }
}
