package br.com.vigiauto.data.repo

import br.com.vigiauto.data.db.VehicleRecordDao
import br.com.vigiauto.data.db.VehicleRecordEntity
import br.com.vigiauto.data.db.toDomain
import br.com.vigiauto.data.location.LocationCapture
import br.com.vigiauto.data.lookup.LookupResult
import br.com.vigiauto.data.lookup.LookupRouter
import br.com.vigiauto.data.lookup.NmtBackgroundWorker
import br.com.vigiauto.data.network.ConnectivityMonitor
import br.com.vigiauto.data.photos.PhotoFrameSource
import br.com.vigiauto.data.photos.PhotoStorage
import br.com.vigiauto.data.photos.VehiclePhotoCapture
import br.com.vigiauto.data.photos.VehiclePhotoGeometry
import br.com.vigiauto.data.prefs.SettingsRepository
import br.com.vigiauto.domain.model.PhotoQuality
import br.com.vigiauto.domain.model.PlateCandidate
import br.com.vigiauto.domain.model.PlateCapture
import br.com.vigiauto.domain.model.PlateQuality
import br.com.vigiauto.domain.model.QueryStatus
import br.com.vigiauto.domain.model.RestrictionStatus
import br.com.vigiauto.domain.model.VehicleData
import br.com.vigiauto.domain.model.VehicleRecord
import br.com.vigiauto.domain.plate.PlateBox
import br.com.vigiauto.domain.plate.PlateNormalizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val MAX_ALTERNATIVE_QUERIES = 6
private const val FAILED_LOOKUP_COOLDOWN_MS = 60_000L

data class DetectionResult(
    val id: Long,
    val normalizedPlate: String,
    val displayPlate: String,
    val isNew: Boolean,
    val timestamp: Long
)

data class ManualQueryResult(val normalizedPlate: String?, val message: String)

private sealed interface LookupOutcome
private data class FoundLookup(val plate: String, val data: VehicleData) : LookupOutcome
private data class FailedLookup(val status: QueryStatus, val message: String?) : LookupOutcome
private data class CapturedPhoto(
    val jpeg: ByteArray,
    val timestamp: Long,
    val quality: PhotoQuality,
    val plateCropJpeg: ByteArray? = null,
    val plateBox: PlateBox? = null,
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
    val plateQuality: PlateQuality = PlateQuality.UNAVAILABLE,
    val needsReview: Boolean = false,
    val cropAspectRatio: Float? = null,
    val detectorConfidence: Float? = null,
    val ocrConfidence: Float? = null,
    val ocrText: String? = null
)

/**
 * Repositório único do aplicativo.
 *
 * Regras centrais:
 * - chave única por placa normalizada (índice UNIQUE no Room);
 * - releituras atualizam última visualização/ocorrências e não criam registros;
 * - a fotografia original nunca é substituída automaticamente;
 * - consultas têm concorrência limitada, deduplicação por placa e continuam
 *   ativas independentemente da navegação entre telas.
 */
class VehicleRepository(
    private val dao: VehicleRecordDao,
    private val lookupRouter: LookupRouter,
    private val photoStorage: PhotoStorage,
    private val frameSource: PhotoFrameSource,
    private val settings: SettingsRepository,
    private val connectivity: ConnectivityMonitor,
    private val nmtWorker: NmtBackgroundWorker,
    private val locationCapture: LocationCapture,
    private val scope: CoroutineScope
) {

    private val queryJobs = mutableMapOf<String, Job>()
    private val queryJobsLock = Any()
    private val detectionJobs = mutableMapOf<String, Deferred<DetectionResult?>>()
    private val detectionJobsLock = Any()
    private val failedLookups = mutableMapOf<String, Long>()
    private val failedLookupsLock = Any()
    // Três consultas simultâneas acompanham o limite de três caixas ONNX por
    // quadro, para que uma sequência de veículos não espere uma consulta por vez.
    private val querySemaphore = Semaphore(3)
    private val exportJson = Json { prettyPrint = true }

    /**
     * Correções confirmadas pela consulta (leitura ambígua -> placa encontrada).
     * Mantidas em memória para que a mesma leitura errada, enquanto o app está
     * aberto, reutilize a placa corrigida em vez de criar um registro duplicado.
     */
    private val correctedPlates = mutableMapOf<String, String>()
    private val correctionLock = Any()

    val records: Flow<List<VehicleRecord>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    val uniqueCount: Flow<Int> = dao.observeCount()
    val pendingCount: Flow<Int> = dao.observePendingCount()
    val online: Flow<Boolean> = connectivity.isOnline

    fun start() {
        nmtWorker.start()
        scope.launch {
            // Versões anteriores criavam um registro antes da consulta. Remove
            // os registros antigos que ficaram sem qualquer dado do veículo.
            runCatching { removeRecordsWithoutVehicleData() }
            runCatching { photoStorage.cleanupOrphans(dao.allPhotoPaths().toSet()) }
            runCatching { enforceLimits() }
        }
        scope.launch {
            connectivity.isOnline.collect { online ->
                if (online) runCatching { retryConnectionFailures() }
            }
        }
    }

    fun observeById(id: Long): Flow<VehicleRecord?> =
        dao.observeById(id).map { it?.toDomain() }

    fun observeByPlate(plate: String): Flow<VehicleRecord?> =
        dao.observeByPlate(plate).map { it?.toDomain() }

    /**
     * Confirma uma leitura e só grava no histórico depois que a consulta
     * devolve dados do veículo. Leituras sem resultado (placa não encontrada,
     * falha de rede, resposta vazia etc.) não criam registro nem fotografia.
     *
     * Detecções simultâneas da mesma placa compartilham a mesma consulta para
     * não gerar chamadas duplicadas nem registros concorrentes.
     */
    suspend fun recordDetection(candidate: PlateCandidate): DetectionResult? {
        val detected = PlateNormalizer.normalize(candidate.normalized) ?: return null
        val normalized = applyCorrection(detected)
        val now = System.currentTimeMillis()

        val existing = dao.findByPlate(normalized)
        if (existing != null && existing.hasAnyVehicleData()) {
            dao.touchSeen(normalized, now, candidate.confidence)
            captureLocation(existing.id)
            maybeRequery(existing, now)
            // Se a captura anterior não gerou fotografia, aproveita esta
            // confirmação sem criar um segundo registro. A melhor foto já
            // existente continua preservada.
            if (existing.photoPath == null) {
                scope.launch {
                    runCatching {
                        val captured = candidate.capture?.toCapturedPhoto(candidate)
                            ?: captureFrame(candidate)
                        if (captured != null) saveCaptureIfAbsent(existing.id, captured)
                    }
                }
            }
            scope.launch { runCatching { enforceLimits() } }
            return DetectionResult(
                id = existing.id,
                normalizedPlate = existing.normalizedPlate,
                displayPlate = existing.displayPlate,
                isNew = false,
                timestamp = now
            )
        }

        val job = synchronized(detectionJobsLock) {
            detectionJobs[normalized] ?: scope.async {
                detectAndPersist(candidate, normalized, now)
            }.also { started ->
                detectionJobs[normalized] = started
                started.invokeOnCompletion {
                    synchronized(detectionJobsLock) {
                        if (detectionJobs[normalized] === started) {
                            detectionJobs.remove(normalized)
                        }
                    }
                }
            }
        }

        return job.await()
    }

    private suspend fun detectAndPersist(
        candidate: PlateCandidate,
        normalized: String,
        detectedAt: Long
    ): DetectionResult? {
        if (isRecentFailure(normalized, detectedAt)) return null

        val photo = if (candidate.capture == null) {
            scope.async(Dispatchers.Default) { captureFrame(candidate) }
        } else {
            null
        }

        val outcome = try {
            resolveLookup(
                plate = normalized,
                alternatives = PlateNormalizer.alternatives(normalized)
            )
        } catch (cancelled: CancellationException) {
            photo?.cancel()
            throw cancelled
        } catch (_: Throwable) {
            photo?.cancel()
            markFailure(normalized, detectedAt)
            deleteIfEmpty(normalized)
            return null
        }

        if (outcome is FailedLookup) {
            photo?.cancel()
            markFailure(normalized, detectedAt)
            deleteIfEmpty(normalized)
            return null
        }

        val found = outcome as FoundLookup
        if (found.plate != normalized) {
            rememberCorrection(normalized, found.plate)
            deleteIfEmpty(normalized)
        }
        markSuccess(normalized, found.plate)

        val captured = candidate.capture?.toCapturedPhoto(candidate) ?: try {
            photo?.await()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }

        return persistFound(candidate, found, captured, detectedAt)
    }

    private suspend fun persistFound(
        candidate: PlateCandidate,
        found: FoundLookup,
        photo: CapturedPhoto?,
        detectedAt: Long
    ): DetectionResult? {
        val now = System.currentTimeMillis()
        val status = if (found.data.incomplete) QueryStatus.INCOMPLETE else QueryStatus.COMPLETED
        val existing = dao.findByPlate(found.plate)

        val id: Long
        val isNew: Boolean
        if (existing == null) {
            val inserted = dao.insertIgnore(
                VehicleRecordEntity(
                    normalizedPlate = found.plate,
                    displayPlate = PlateNormalizer.display(found.plate),
                    brand = found.data.brand,
                    model = found.data.model,
                    version = found.data.version,
                    color = found.data.color,
                    manufactureYear = found.data.manufactureYear,
                    modelYear = found.data.modelYear,
                    vehicleCategory = found.data.vehicleCategory,
                    city = found.data.city,
                    state = found.data.state,
                    restrictionStatus = found.data.restrictionStatus.name,
                    restrictionDescription = found.data.restrictionDescription,
                    sourceName = found.data.sourceName,
                    firstDetectionTimestamp = detectedAt,
                    lastSeenTimestamp = detectedAt,
                    queryTimestamp = now,
                    queryStatus = status.name,
                    recognitionConfidence = candidate.confidence
                )
            )
            if (inserted == -1L) {
                // Corrida: outra detecção inseriu a mesma placa.
                val current = dao.findByPlate(found.plate) ?: return null
                dao.touchSeen(found.plate, detectedAt, candidate.confidence)
                applyFoundLookup(found, status, now)
                id = current.id
                isNew = false
            } else {
                id = inserted
                isNew = true
            }
        } else {
            dao.touchSeen(found.plate, detectedAt, candidate.confidence)
            applyFoundLookup(found, status, now)
            id = existing.id
            isNew = false
        }

        // A localização é atualizada em segundo plano. A gravação da placa e a
        // consulta nunca esperam o GPS/Geocoder, preservando a leitura em
        // sequência de vários veículos.
        captureLocation(id)

        if (photo != null) {
            runCatching { saveCaptureIfAbsent(id, photo) }
        }

        // A consulta NMT entra na fila persistente do worker. Ela nunca
        // bloqueia a leitura, a UI ou o retorno deste método.
        nmtWorker.wake()

        scope.launch { runCatching { enforceLimits() } }

        return DetectionResult(
            id = id,
            normalizedPlate = found.plate,
            displayPlate = PlateNormalizer.display(found.plate),
            isNew = isNew,
            timestamp = detectedAt
        )
    }

    private suspend fun applyFoundLookup(
        found: FoundLookup,
        status: QueryStatus,
        timestamp: Long
    ) {
        dao.applyLookup(
            plate = found.plate,
            status = status.name,
            timestamp = timestamp,
            brand = found.data.brand,
            model = found.data.model,
            version = found.data.version,
            color = found.data.color,
            manufactureYear = found.data.manufactureYear,
            modelYear = found.data.modelYear,
            category = found.data.vehicleCategory,
            city = found.data.city,
            state = found.data.state,
            restrictionStatus = found.data.restrictionStatus.name,
            restrictionDescription = found.data.restrictionDescription,
            sourceName = found.data.sourceName
        )
    }

    private fun captureFrame(candidate: PlateCandidate): CapturedPhoto? {
        val frame = frameSource.latestFrames()
            .maxWithOrNull(compareBy({ it.sharpness }, { it.timestamp }))
            ?: return null
        val box = candidate.box.takeIf { it.width >= 4f && it.height >= 2f }
        val capture = VehiclePhotoCapture.create(
            frame = frame,
            plateBox = box,
            capturedAt = frame.timestamp,
            candidatesInFrame = candidate.candidatesInFrame,
            detectorConfidence = candidate.detectorConfidence,
            ocrConfidence = candidate.ocrConfidence,
            textMismatch = candidate.textMismatch()
        ) ?: return null
        return capture.toCapturedPhoto(candidate)
    }

    private fun PlateCapture.toCapturedPhoto(candidate: PlateCandidate): CapturedPhoto =
        CapturedPhoto(
            jpeg = photoJpeg,
            timestamp = capturedAt,
            quality = photoQuality,
            plateCropJpeg = plateCropJpeg,
            plateBox = plateBox.takeIf { it.width > 0f && it.height > 0f },
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            plateQuality = plateQuality,
            needsReview = needsReview,
            cropAspectRatio = cropAspectRatio,
            detectorConfidence = candidate.detectorConfidence,
            ocrConfidence = candidate.ocrConfidence,
            ocrText = candidate.rawText
        )

    private fun PlateCandidate.textMismatch(): Boolean {
        val ocrNormalized = PlateNormalizer.normalize(rawText) ?: return true
        return ocrNormalized != normalized
    }

    /**
     * Salva a fotografia e o recorte com nomes ligados ao ID do registro. O
     * UPDATE só grava se ainda não existir fotografia; se outra execução venceu
     * a corrida, os arquivos recém-gravados são apagados.
     */
    private suspend fun saveCaptureIfAbsent(recordId: Long, captured: CapturedPhoto): Boolean {
        val photoName = photoStorage.saveJpeg(captured.jpeg, "vehicle_$recordId") ?: return false
        val cropName = captured.plateCropJpeg?.let {
            photoStorage.saveJpeg(it, "plate_$recordId")
        }
        val box = captured.plateBox
        val sourceWidth = captured.sourceWidth
        val sourceHeight = captured.sourceHeight
        val written = runCatching {
            dao.setCaptureIfAbsent(
                id = recordId,
                photoPath = photoName,
                timestamp = captured.timestamp,
                quality = captured.quality.name,
                cropPath = cropName,
                ocrText = captured.ocrText?.takeIf { it.isNotBlank() },
                boxLeft = box?.let { VehiclePhotoGeometry.normalize(it.left, sourceWidth) },
                boxTop = box?.let { VehiclePhotoGeometry.normalize(it.top, sourceHeight) },
                boxRight = box?.let { VehiclePhotoGeometry.normalize(it.right, sourceWidth) },
                boxBottom = box?.let { VehiclePhotoGeometry.normalize(it.bottom, sourceHeight) },
                detectorConfidence = captured.detectorConfidence,
                ocrConfidence = captured.ocrConfidence,
                cropAspectRatio = captured.cropAspectRatio,
                plateQuality = if (cropName != null) {
                    captured.plateQuality.name
                } else {
                    PlateQuality.UNAVAILABLE.name
                },
                needsReview = captured.needsReview || cropName == null
            )
        }.getOrDefault(0)

        if (written == 0) {
            photoStorage.delete(photoName)
            photoStorage.delete(cropName)
            return false
        }
        return true
    }

    private suspend fun resolveLookup(
        plate: String,
        alternatives: List<String>
    ): LookupOutcome {
        val primary = lookup(plate)
        val primaryFailureMessage: String?
        if (primary is LookupResult.Success) {
            if (primary.data.hasAnyData()) return FoundLookup(plate, primary.data)
            // Alguns serviços devolvem sucesso com o veículo vazio para uma
            // placa inexistente. Isso também é "não encontrado" e precisa
            // acionar a variante O/0 da quinta posição.
            primaryFailureMessage = "A consulta não retornou dados do veículo."
        } else {
            val primaryFailure = primary as? LookupResult.Failure
                ?: return FailedLookup(QueryStatus.INVALID_RESPONSE, null)
            if (primaryFailure.status != QueryStatus.NOT_FOUND) {
                return FailedLookup(primaryFailure.status, primaryFailure.message)
            }
            primaryFailureMessage = primaryFailure.message
        }

        for (alternative in lookupVariants(plate, alternatives)) {
            when (val result = lookup(alternative)) {
                is LookupResult.Success -> {
                    if (result.data.hasAnyData()) return FoundLookup(alternative, result.data)
                }
                is LookupResult.Failure -> {
                    if (result.status != QueryStatus.NOT_FOUND) {
                        return FailedLookup(result.status, result.message)
                    }
                }
            }
        }

        return FailedLookup(QueryStatus.NOT_FOUND, primaryFailureMessage)
    }

    /**
     * Ordem de consulta das hipóteses de uma leitura ambígua. A troca O/0 da
     * quinta posição vem primeiro porque é a única em que o padrão Mercosul e
     * o antigo produzem placas válidas diferentes sem que a posição determine
     * o caractere.
     */
    private fun lookupVariants(plate: String, alternatives: List<String>): List<String> =
        buildList {
            PlateNormalizer.fifthOZeroVariant(plate)?.let(::add)
            addAll(alternatives)
        }.filter { it != plate }
            .distinct()
            .take(MAX_ALTERNATIVE_QUERIES)

    private fun isRecentFailure(plate: String, now: Long): Boolean = synchronized(failedLookupsLock) {
        val failedAt = failedLookups[plate] ?: return@synchronized false
        if (now - failedAt > FAILED_LOOKUP_COOLDOWN_MS) {
            failedLookups.remove(plate)
            false
        } else {
            true
        }
    }

    private fun markFailure(plate: String, now: Long) {
        synchronized(failedLookupsLock) {
            failedLookups[plate] = now
        }
    }

    private fun markSuccess(vararg plates: String) {
        synchronized(failedLookupsLock) {
            plates.forEach { failedLookups.remove(it) }
        }
    }

    private suspend fun deleteIfEmpty(plate: String) {
        val record = dao.findByPlate(plate) ?: return
        if (record.hasAnyVehicleData()) return
        dao.deleteById(record.id)
        photoStorage.delete(record.photoPath)
        photoStorage.delete(record.plateCropPath)
    }

    private suspend fun removeRecordsWithoutVehicleData() {
        dao.getAll()
            .filterNot { it.hasAnyVehicleData() }
            .forEach { record ->
                dao.deleteById(record.id)
                photoStorage.delete(record.photoPath)
                photoStorage.delete(record.plateCropPath)
            }
    }

    private fun VehicleRecordEntity.hasAnyVehicleData(): Boolean =
        !brand.isNullOrBlank() || !model.isNullOrBlank() || !version.isNullOrBlank() ||
            !color.isNullOrBlank() || manufactureYear != null || modelYear != null ||
            !vehicleCategory.isNullOrBlank() ||
            (restrictionStatus == RestrictionStatus.CONFIRMED.name &&
                !restrictionDescription.isNullOrBlank())

    private fun VehicleData.hasAnyData(): Boolean =
        !brand.isNullOrBlank() || !model.isNullOrBlank() || !version.isNullOrBlank() ||
            !color.isNullOrBlank() || manufactureYear != null || modelYear != null ||
            !vehicleCategory.isNullOrBlank() ||
            (restrictionStatus == RestrictionStatus.CONFIRMED &&
                !restrictionDescription.isNullOrBlank())

    private suspend fun maybeRequery(record: VehicleRecordEntity, now: Long) {
        if (record.queryStatus == QueryStatus.IN_PROGRESS.name || record.queryStatus == QueryStatus.PENDING.name) return
        val config = settings.settingsFlow.first()
        val lastQuery = record.queryTimestamp ?: 0L
        val elapsedMinutes = (now - lastQuery) / 60_000L
        if (elapsedMinutes >= config.minRepeatQueryMinutes) {
            startQuery(record.normalizedPlate, PlateNormalizer.alternatives(record.normalizedPlate))
        }
    }

    suspend fun requery(recordId: Long): Boolean {
        val record = dao.findById(recordId) ?: return false
        dao.updateQueryStatus(record.normalizedPlate, QueryStatus.PENDING.name, System.currentTimeMillis())
        startQuery(record.normalizedPlate, PlateNormalizer.alternatives(record.normalizedPlate))
        requeryNmt(recordId)
        return true
    }

    /**
     * Coloca a categoria NMT deste registro no início da fila. A categoria já
     * concluída não é apagada; ela só é substituída quando a nova consulta
     * terminar com um resultado reconhecido.
     */
    suspend fun requeryNmt(recordId: Long): Boolean {
        dao.findById(recordId) ?: return false
        dao.resetNmtQueue(recordId)
        nmtWorker.wake()
        return true
    }

    suspend fun manualQuery(plateInput: String): ManualQueryResult {
        val normalized = PlateNormalizer.normalize(plateInput)
            ?: return ManualQueryResult(null, "Placa inválida. Use o formato ABC1234 ou ABC1D23.")

        val existing = dao.findByPlate(normalized)
        val outcome = try {
            resolveLookup(normalized, PlateNormalizer.alternatives(normalized))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            FailedLookup(QueryStatus.CONNECTION_FAILURE, "Falha ao consultar o serviço.")
        }

        if (outcome is FailedLookup) {
            deleteIfEmpty(normalized)
            val detail = when (outcome.status) {
                QueryStatus.NOT_FOUND -> "Veículo não encontrado na base consultada."
                else -> outcome.message ?: "Não foi possível concluir a consulta."
            }
            val kept = existing?.hasAnyVehicleData() == true
            return ManualQueryResult(
                normalized,
                "$detail ${if (kept) "O registro anterior foi mantido." else "Nenhum registro foi criado."}"
            )
        }

        val found = outcome as FoundLookup
        if (found.plate != normalized) {
            rememberCorrection(normalized, found.plate)
            deleteIfEmpty(normalized)
        }
        markSuccess(normalized, found.plate)

        val result = persistFound(
            candidate = PlateCandidate(
                rawText = found.plate,
                normalized = found.plate,
                confidence = 1f
            ),
            found = found,
            photo = null,
            detectedAt = System.currentTimeMillis()
        ) ?: return ManualQueryResult(
            normalized,
            "A consulta encontrou o veículo, mas não foi possível salvar o registro."
        )

        val action = if (result.isNew) {
            "Veículo encontrado e adicionado ao histórico."
        } else {
            "Dados do veículo atualizados no histórico."
        }
        return ManualQueryResult(result.normalizedPlate, action)
    }

    private fun startQuery(plate: String, alternatives: List<String> = emptyList()) {
        synchronized(queryJobsLock) {
            if (queryJobs[plate]?.isActive == true) return
            val job = scope.launch { executeQuery(plate, alternatives) }
            queryJobs[plate] = job
            job.invokeOnCompletion {
                synchronized(queryJobsLock) {
                    if (queryJobs[plate] === job) queryJobs.remove(plate)
                }
            }
        }
    }

    private suspend fun executeQuery(plate: String, alternatives: List<String>) {
        dao.updateQueryStatus(plate, QueryStatus.IN_PROGRESS.name, System.currentTimeMillis())
        var resultPlate = plate
        var result = lookup(plate)
        if (result is LookupResult.Success && !result.data.hasAnyData()) {
            result = LookupResult.Failure(
                QueryStatus.NOT_FOUND,
                "A consulta não retornou dados do veículo."
            )
        }

        // Uma leitura ambígua pode gerar uma placa válida que não existe. Só
        // nesse caso vale gastar consultas extras com as hipóteses visuais.
        if (result is LookupResult.Failure && result.status == QueryStatus.NOT_FOUND) {
            for (alternative in lookupVariants(plate, alternatives)) {
                when (val alternativeResult = lookup(alternative)) {
                    is LookupResult.Success -> {
                        if (renameRecord(plate, alternative)) {
                            resultPlate = alternative
                            result = alternativeResult
                            rememberCorrection(plate, alternative)
                        }
                        break
                    }
                    is LookupResult.Failure -> {
                        if (alternativeResult.status != QueryStatus.NOT_FOUND) break
                    }
                }
            }
        }

        val finishedAt = System.currentTimeMillis()

        when (result) {
            is LookupResult.Success -> {
                val data = result.data
                val status = if (data.incomplete) QueryStatus.INCOMPLETE else QueryStatus.COMPLETED
                dao.applyLookup(
                    plate = resultPlate,
                    status = status.name,
                    timestamp = finishedAt,
                    brand = data.brand,
                    model = data.model,
                    version = data.version,
                    color = data.color,
                    manufactureYear = data.manufactureYear,
                    modelYear = data.modelYear,
                    category = data.vehicleCategory,
                    city = data.city,
                    state = data.state,
                    restrictionStatus = data.restrictionStatus.name,
                    restrictionDescription = data.restrictionDescription,
                    sourceName = data.sourceName
                )
            }
            is LookupResult.Failure -> {
                dao.updateQueryStatus(resultPlate, result.status.name, finishedAt)
            }
        }
    }

    private suspend fun lookup(plate: String): LookupResult = runCatching {
        querySemaphore.withPermit { lookupRouter.lookup(plate) }
    }.getOrElse { error ->
        LookupResult.Failure(QueryStatus.CONNECTION_FAILURE, error.message)
    }

    private suspend fun renameRecord(oldPlate: String, newPlate: String): Boolean {
        val record = dao.findByPlate(oldPlate) ?: return false
        val renamed = dao.renamePlate(
            id = record.id,
            newPlate = newPlate,
            newDisplay = PlateNormalizer.display(newPlate)
        ) > 0
        if (renamed) nmtWorker.wake()
        return renamed
    }

    private suspend fun applyCorrection(plate: String): String {
        synchronized(correctionLock) {
            correctedPlates[plate]
        }?.let { return it }

        val stored = runCatching { settings.plateCorrection(plate) }.getOrNull() ?: return plate
        synchronized(correctionLock) {
            correctedPlates[plate] = stored
        }
        return stored
    }

    private suspend fun rememberCorrection(wrong: String, right: String) {
        synchronized(correctionLock) {
            correctedPlates[wrong] = right
        }
        runCatching { settings.setPlateCorrection(wrong, right) }
    }

    private fun captureLocation(recordId: Long) {
        scope.launch {
            runCatching {
                val location = locationCapture.capture() ?: return@runCatching
                dao.updateLocation(
                    id = recordId,
                    street = location.street,
                    number = location.number,
                    neighborhood = location.neighborhood,
                    city = location.city,
                    state = location.state,
                    postalCode = location.postalCode,
                    capturedAt = location.capturedAt
                )
            }
        }
    }

    suspend fun attachPhoto(recordId: Long, jpegBytes: ByteArray): Boolean {
        val previous = dao.findById(recordId) ?: return false
        val fileName = photoStorage.saveJpeg(jpegBytes, "vehicle_$recordId") ?: return false
        dao.replacePhoto(recordId, fileName, System.currentTimeMillis(), PhotoQuality.GOOD.name)
        if (!previous.photoPath.isNullOrBlank() && previous.photoPath != fileName) {
            photoStorage.delete(previous.photoPath)
        }
        photoStorage.delete(previous.plateCropPath)
        enforceLimits()
        return true
    }

    suspend fun removePhoto(recordId: Long) {
        val previous = dao.findById(recordId) ?: return
        dao.clearPhoto(recordId)
        photoStorage.delete(previous.photoPath)
        photoStorage.delete(previous.plateCropPath)
    }

    suspend fun deleteRecord(recordId: Long) {
        val record = dao.findById(recordId) ?: return
        dao.deleteById(recordId)
        photoStorage.delete(record.photoPath)
        photoStorage.delete(record.plateCropPath)
    }

    suspend fun deleteAllRecords(deletePhotos: Boolean = true) {
        val photos = if (deletePhotos) dao.allPhotoPaths() else emptyList()
        dao.deleteAll()
        photos.forEach { photoStorage.delete(it) }
    }

    suspend fun deleteAllPhotos() {
        val photos = dao.allPhotoPaths()
        dao.getAll().forEach { dao.clearPhoto(it.id) }
        photos.forEach { photoStorage.delete(it) }
    }

    suspend fun storageUsageBytes(): Long = photoStorage.totalBytes()

    suspend fun enforceLimits() {
        val config = settings.settingsFlow.first()

        if (config.retentionDays > 0) {
            val threshold = System.currentTimeMillis() - config.retentionDays * 86_400_000L
            dao.olderThan(threshold).forEach { record ->
                dao.deleteById(record.id)
                photoStorage.delete(record.photoPath)
                photoStorage.delete(record.plateCropPath)
            }
        }

        val all = dao.getAll()
        if (all.size > config.maxRecords) {
            dao.oldest(all.size - config.maxRecords).forEach { record ->
                dao.deleteById(record.id)
                photoStorage.delete(record.photoPath)
                photoStorage.delete(record.plateCropPath)
            }
        }

        val maxPhotoBytes = config.photoStorageLimitMb.coerceAtLeast(1) * 1024L * 1024L
        var total = photoStorage.totalBytes()
        if (total > maxPhotoBytes) {
            val oldestFirst = dao.getAll().sortedBy { it.lastSeenTimestamp }
            for (record in oldestFirst) {
                if (total <= maxPhotoBytes) break
                val path = record.photoPath ?: continue
                val size = (photoStorage.resolve(path)?.length() ?: 0L) +
                    (photoStorage.resolve(record.plateCropPath)?.length() ?: 0L)
                if (photoStorage.delete(path)) {
                    photoStorage.delete(record.plateCropPath)
                    dao.clearPhoto(record.id)
                    total -= size
                }
            }
        }

        photoStorage.cleanupOrphans(dao.allPhotoPaths().toSet())
    }

    private suspend fun retryConnectionFailures() {
        dao.byStatus(QueryStatus.CONNECTION_FAILURE.name).forEach { record ->
            startQuery(record.normalizedPlate, PlateNormalizer.alternatives(record.normalizedPlate))
        }
    }

    suspend fun exportRecordsJson(): String {
        val records = dao.getAll()
        val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault())
        val array: JsonArray = buildJsonArray {
            records.forEach { record ->
                addJsonObject {
                    put("placa", record.displayPlate)
                    put("placaNormalizada", record.normalizedPlate)
                    put("marca", record.brand)
                    put("modelo", record.model)
                    put("versao", record.version)
                    put("cor", record.color)
                    put("anoFabricacao", record.manufactureYear)
                    put("anoModelo", record.modelYear)
                    put("categoria", record.vehicleCategory)
                    put("cidade", record.city)
                    put("estado", record.state)
                    put("localizacaoRua", record.locationStreet)
                    put("localizacaoNumero", record.locationNumber)
                    put("localizacaoBairro", record.locationNeighborhood)
                    put("localizacaoCidade", record.locationCity)
                    put("localizacaoEstado", record.locationState)
                    put("localizacaoCep", record.locationPostalCode)
                    put("localizacaoCapturadaEm", record.locationCapturedAt?.let {
                        formatter.format(Instant.ofEpochMilli(it))
                    })
                    put("nmtStatus", record.nmtStatus)
                    put("nmtConsultadoEm", record.nmtCheckedAt?.let {
                        formatter.format(Instant.ofEpochMilli(it))
                    })
                    put("situacaoRestricao", record.restrictionStatus)
                    put("descricaoRestricao", record.restrictionDescription)
                    put("fonte", record.sourceName)
                    put("primeiraIdentificacao", formatter.format(Instant.ofEpochMilli(record.firstDetectionTimestamp)))
                    put("ultimaVisualizacao", formatter.format(Instant.ofEpochMilli(record.lastSeenTimestamp)))
                    put("ultimaConsulta", record.queryTimestamp?.let { formatter.format(Instant.ofEpochMilli(it)) })
                    put("statusConsulta", record.queryStatus)
                    put("confianca", record.recognitionConfidence)
                    put("ocorrencias", record.occurrenceCount)
                    put("fotografia", record.photoPath)
                    put("qualidadeFotografia", record.photoQualityStatus)
                    put("recortePlaca", record.plateCropPath)
                    put("textoOcrPlaca", record.plateOcrText)
                    put("placaCaixaEsquerda", record.plateBoxLeft)
                    put("placaCaixaTopo", record.plateBoxTop)
                    put("placaCaixaDireita", record.plateBoxRight)
                    put("placaCaixaBase", record.plateBoxBottom)
                    put("confiancaDeteccaoPlaca", record.plateDetectorConfidence)
                    put("confiancaOcrPlaca", record.plateOcrConfidence)
                    put("proporcaoRecortePlaca", record.plateCropAspectRatio)
                    put("qualidadePlaca", record.plateQualityStatus)
                    put("verificacaoPlacaRecomendada", record.plateNeedsReview)
                }
            }
        }
        return exportJson.encodeToString(JsonArray.serializer(), array)
    }
}
