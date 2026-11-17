package br.com.vigiauto.data.lookup

import android.content.Context
import android.util.Log
import br.com.vigiauto.data.db.VehicleRecordDao
import br.com.vigiauto.data.db.VehicleRecordEntity
import br.com.vigiauto.domain.model.NmtCategoryPolicy
import br.com.vigiauto.domain.model.NmtStatus
import br.com.vigiauto.domain.plate.PlateNormalizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

private const val TAG = "NmtBackground"
private const val SUCCESS_TTL_MS = 24 * 60 * 60 * 1000L
private const val SHORT_ERROR_TTL_MS = 15 * 60 * 1000L
private const val LONG_ERROR_TTL_MS = 60 * 60 * 1000L
private const val UNKNOWN_TTL_MS = 6 * 60 * 60 * 1000L
private const val INVALID_PLATE_TTL_MS = 7 * 24 * 60 * 60 * 1000L
private const val LEASE_MS = 5 * 60 * 1000L
private const val IDLE_WAIT_MS = 30_000L
private const val MAX_ATTEMPTS = 3
private const val RATE_LIMIT_MIN_MS = 2_000L
private const val RATE_LIMIT_JITTER_MS = 3_000L
private const val CIRCUIT_BREAKER_MS = 30 * 60 * 1000L

/**
 * Worker silencioso da consulta OTTC/NMT.
 *
 * Não conhece Compose, ViewModel, Activity ou qualquer recurso visual. Ele
 * consome a fila persistida em `vehicle_records`, executa uma consulta por
 * vez, grava o resultado no Room e usa `nmtNextAttemptAt` para cache,
 * retentativa e retomada após reinício do processo.
 *
 * O gatilho é o próprio cadastro do veículo: sempre que uma placa é
 * persistida, `VehicleRepository` chama [wake]. Também há uma varredura na
 * inicialização do app para retomar pendências antigas.
 */
class NmtBackgroundWorker(
    context: Context,
    private val dao: VehicleRecordDao,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis
) {

    private val appContext = context.applicationContext
    private val wakeSignal = Channel<Unit>(Channel.CONFLATED)
    private val prefs = appContext.getSharedPreferences("nmt_worker", Context.MODE_PRIVATE)
    private var workerJob: Job? = null

    fun start() {
        if (workerJob?.isActive == true) return
        workerJob = scope.launch(Dispatchers.Default) {
            reopenUnresolvedQueueOncePerVersion()
            loop()
        }
    }

    /**
     * Depois de uma atualização, registros cujo resultado foi apagado por uma
     * falha antiga voltam para o início da fila. Categorias já concluídas não
     * são tocadas. A marca fica por versão para não reconsultar tudo a cada
     * abertura do app.
     */
    private suspend fun reopenUnresolvedQueueOncePerVersion() {
        val version = runCatching {
            appContext.packageManager
                .getPackageInfo(appContext.packageName, 0)
                .versionName
        }.getOrNull() ?: return
        if (prefs.getString("queue_reopened_version", null) == version) return

        val reopened = runCatching { dao.resetUnresolvedNmtQueue() }.getOrNull() ?: return
        prefs.edit().putString("queue_reopened_version", version).apply()
        if (reopened > 0) {
            Log.i(TAG, "Fila NMT reaberta para $reopened registro(s) sem categoria")
        }
    }

    fun wake() {
        wakeSignal.trySend(Unit)
    }

    private suspend fun loop() {
        var consecutiveUnavailable = 0

        while (currentCoroutineContext().isActive) {
            val now = clock()
            val candidate = runCatching { dao.nextNmtCandidate(now) }.getOrNull()
            if (candidate == null) {
                withTimeoutOrNull(IDLE_WAIT_MS) { wakeSignal.receive() }
                continue
            }

            val claimed = runCatching {
                dao.claimNmt(candidate.id, now + LEASE_MS)
            }.isSuccess
            if (!claimed) {
                delay(1_000L)
                continue
            }

            val finalStatus = process(candidate, now)
            consecutiveUnavailable = if (finalStatus == NmtStatus.UNAVAILABLE) {
                consecutiveUnavailable + 1
            } else {
                0
            }

            if (consecutiveUnavailable >= 3) {
                // Evita martelar o site quando ele está bloqueando o WebView.
                // O processo continua responsivo; apenas a fila espera.
                delay(CIRCUIT_BREAKER_MS)
                consecutiveUnavailable = 0
            } else {
                delay(RATE_LIMIT_MIN_MS + Random.nextLong(0L, RATE_LIMIT_JITTER_MS))
            }
        }
    }

    private suspend fun process(record: VehicleRecordEntity, startedAt: Long): NmtStatus {
        val normalized = PlateNormalizer.normalize(record.normalizedPlate)
        if (normalized == null) {
            save(
                record = record,
                status = NmtStatus.INVALID_PLATE,
                rawStatus = null,
                now = clock(),
                ttlMs = INVALID_PLATE_TTL_MS,
                attempts = 0
            )
            log(record, NmtStatus.INVALID_PLATE, startedAt, 1, "invalid_plate")
            return NmtStatus.INVALID_PLATE
        }

        var lastResult = NmtPageResult(NmtStatus.UNAVAILABLE, errorCode = "not_started")
        var attempt = 0

        while (attempt < MAX_ATTEMPTS) {
            attempt++
            val result = try {
                NmtWebViewSession(appContext, normalized).query()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                NmtPageResult(NmtStatus.UNAVAILABLE, errorCode = "exception")
            }

            lastResult = result
            if (result.status != NmtStatus.UNAVAILABLE) break
            if (attempt < MAX_ATTEMPTS) {
                delay(2_000L * attempt)
            }
        }

        val final = lastResult
        val now = clock()
        val ttlMs = when (final.status) {
            NmtStatus.REGULAR, NmtStatus.IRREGULAR, NmtStatus.NOT_REGISTERED -> SUCCESS_TTL_MS
            NmtStatus.INVALID_PLATE -> INVALID_PLATE_TTL_MS
            NmtStatus.UNKNOWN -> UNKNOWN_TTL_MS
            NmtStatus.UNAVAILABLE -> {
                if (record.nmtAttempts + 1 >= 3) LONG_ERROR_TTL_MS else SHORT_ERROR_TTL_MS
            }
        }
        val attempts = if (final.status.isResolved) 0 else record.nmtAttempts + 1

        save(
            record = record,
            status = final.status,
            rawStatus = final.rawStatus,
            now = now,
            ttlMs = ttlMs,
            attempts = attempts
        )
        log(record, final.status, startedAt, attempt, final.errorCode)
        return final.status
    }

    private suspend fun save(
        record: VehicleRecordEntity,
        status: NmtStatus,
        rawStatus: String?,
        now: Long,
        ttlMs: Long,
        attempts: Int
    ) {
        val safeRawStatus = rawStatus
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(160)
            ?.takeIf { it.isNotEmpty() }

        runCatching {
            if (NmtCategoryPolicy.shouldPreserve(record.nmtStatus, status)) {
                dao.updateNmtMetadata(
                    id = record.id,
                    checkedAt = now,
                    rawStatus = safeRawStatus,
                    attempts = attempts,
                    nextAttemptAt = now + ttlMs
                )
            } else {
                dao.updateNmtResult(
                    id = record.id,
                    status = status.name,
                    checkedAt = now,
                    rawStatus = safeRawStatus,
                    attempts = attempts,
                    nextAttemptAt = now + ttlMs
                )
            }
        }.onFailure {
            Log.w(TAG, "Falha ao persistir NMT (${it.javaClass.simpleName})")
        }
    }

    private fun log(
        record: VehicleRecordEntity,
        status: NmtStatus,
        startedAt: Long,
        attempts: Int,
        errorCode: String?
    ) {
        val durationMs = (clock() - startedAt).coerceAtLeast(0L)
        Log.i(
            TAG,
            "plate=${maskPlate(record.normalizedPlate)} status=${status.name} " +
                "durationMs=$durationMs attempts=$attempts error=${errorCode ?: "-"}"
        )
    }

    companion object {
        fun maskPlate(plate: String): String =
            if (plate.length >= 5) {
                plate.take(3) + "****" + plate.takeLast(1)
            } else {
                "***"
            }
    }
}
