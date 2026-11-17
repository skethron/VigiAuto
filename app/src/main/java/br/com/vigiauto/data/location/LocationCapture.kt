package br.com.vigiauto.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Obtém uma posição pontual e converte em endereço com o Geocoder do Android.
 *
 * A captura é opcional: se a permissão estiver negada, o GPS estiver desligado
 * ou o aparelho não tiver Geocoder, devolve null e a leitura continua normal.
 * O Mutex evita várias requisições simultâneas quando três ou mais placas são
 * lidas em sequência.
 */
// A permissão é verificada em hasPermission() antes de qualquer chamada ao
// LocationManager; o lint não consegue seguir esse helper.
@SuppressLint("MissingPermission")
class LocationCapture(private val context: Context) {

    private val lock = Mutex()
    private val geocoder = runCatching { Geocoder(context, Locale.getDefault()) }.getOrNull()
    private val geocodeCache = mutableMapOf<String, CachedAddress>()
    private var lastAddress: QueryLocation? = null

    suspend fun capture(): QueryLocation? = lock.withLock {
        if (!hasPermission()) return@withLock null

        // Três placas lidas no mesmo ponto compartilham o endereço recente;
        // não há motivo para abrir uma nova sessão de GPS a cada veículo.
        lastAddress?.takeIf {
            System.currentTimeMillis() - it.capturedAt <= LOCATION_REUSE_MS
        }?.let { return@withLock it }

        val manager = context.getSystemService(LocationManager::class.java)
            ?: return@withLock null

        val location = runCatching { resolveLocation(manager) }
            .onFailure { Log.w(TAG, "Não foi possível obter a localização", it) }
            .getOrNull()

        if (location == null) {
            return@withLock lastAddress?.takeIf {
                System.currentTimeMillis() - it.capturedAt <= FALLBACK_MAX_AGE_MS
            }
        }

        val address = reverseGeocode(location)
        if (address != null) lastAddress = address
        address ?: lastAddress?.takeIf {
            System.currentTimeMillis() - it.capturedAt <= FALLBACK_MAX_AGE_MS
        }
    }

    private fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private suspend fun resolveLocation(manager: LocationManager): Location? {
        val provider = when {
            isEnabled(manager, LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            isEnabled(manager, LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }

        val fresh = provider?.let { currentLocation(manager, it) }
        if (fresh != null) return chooseBetter(fresh, lastKnown(manager))

        val last = lastKnown(manager) ?: return null
        return last.takeIf { System.currentTimeMillis() - it.time <= LAST_KNOWN_MAX_AGE_MS }
    }

    private fun chooseBetter(current: Location, previous: Location?): Location {
        if (previous == null) return current
        val currentAge = (System.currentTimeMillis() - current.time).coerceAtLeast(0L)
        val previousAge = (System.currentTimeMillis() - previous.time).coerceAtLeast(0L)
        // Uma posição atual, mesmo com precisão um pouco pior, representa
        // melhor o local da consulta do que uma posição antiga de outro ponto.
        return if (currentAge <= 30_000L || previousAge > 120_000L) current else previous
    }

    private fun lastKnown(manager: LocationManager): Location? =
        listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }

    private fun isEnabled(manager: LocationManager, provider: String): Boolean =
        runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)

    private suspend fun currentLocation(manager: LocationManager, provider: String): Location? =
        withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getCurrentLocationApi30(manager, provider)
            } else {
                requestSingleUpdate(manager, provider)
            }
        }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun getCurrentLocationApi30(
        manager: LocationManager,
        provider: String
    ): Location? = suspendCancellableCoroutine { continuation ->
        val cancellation = CancellationSignal()
        continuation.invokeOnCancellation { runCatching { cancellation.cancel() } }
        runCatching {
            manager.getCurrentLocation(
                provider,
                cancellation,
                ContextCompat.getMainExecutor(context)
            ) { location ->
                if (continuation.isActive) continuation.resume(location)
            }
        }.onFailure {
            if (continuation.isActive) continuation.resume(null)
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun requestSingleUpdate(
        manager: LocationManager,
        provider: String
    ): Location? = suspendCancellableCoroutine { continuation ->
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                runCatching { manager.removeUpdates(this) }
                if (continuation.isActive) continuation.resume(location)
            }

            override fun onProviderDisabled(provider: String) {
                if (continuation.isActive) continuation.resume(null)
            }

            override fun onProviderEnabled(provider: String) = Unit

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        continuation.invokeOnCancellation {
            runCatching { manager.removeUpdates(listener) }
        }
        runCatching {
            manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        }.onFailure {
            if (continuation.isActive) continuation.resume(null)
        }
    }

    private suspend fun reverseGeocode(location: Location): QueryLocation? {
        val geocoder = geocoder ?: return null
        if (!Geocoder.isPresent()) return null

        val key = String.format(
            Locale.US,
            "%.4f,%.4f",
            location.latitude,
            location.longitude
        )
        val now = System.currentTimeMillis()
        val cached = synchronized(geocodeCache) { geocodeCache[key] }
        if (cached != null && now - cached.checkedAt <= GEOCODE_CACHE_MS) {
            return cached.address?.copy(capturedAt = now)
        }

        val address = withContext(Dispatchers.IO) {
            runCatching { geocoder.getFromLocation(location.latitude, location.longitude, 1) }
                .onFailure { Log.w(TAG, "Falha no Geocoder", it) }
                .getOrNull()
                ?.firstOrNull()
        }

        val result = address?.toQueryLocation(now)
        synchronized(geocodeCache) {
            if (geocodeCache.size > 40) geocodeCache.clear()
            geocodeCache[key] = CachedAddress(result, now)
        }
        return result
    }

    private fun Address.toQueryLocation(capturedAt: Long): QueryLocation? {
        val street = thoroughfare?.trim()?.takeIf { it.isNotEmpty() }
        val number = subThoroughfare?.trim()?.takeIf { it.isNotEmpty() }
        val neighborhood = subLocality?.trim()?.takeIf { it.isNotEmpty() }
        val city = locality?.trim()?.takeIf { it.isNotEmpty() }
            ?: subAdminArea?.trim()?.takeIf { it.isNotEmpty() }
        val state = adminArea?.trim()?.takeIf { it.isNotEmpty() }
        val postalCode = postalCode?.trim()?.takeIf { it.isNotEmpty() }

        val result = QueryLocation(
            street = street,
            number = number,
            neighborhood = neighborhood,
            city = city,
            state = state,
            postalCode = postalCode,
            capturedAt = capturedAt
        )
        return result.takeIf { it.hasAddress }
    }

    private data class CachedAddress(
        val address: QueryLocation?,
        val checkedAt: Long
    )

    private companion object {
        const val TAG = "VigiAutoLocation"
        const val LOCATION_TIMEOUT_MS = 6_000L
        const val LOCATION_REUSE_MS = 10_000L
        const val LAST_KNOWN_MAX_AGE_MS = 10 * 60_000L
        const val FALLBACK_MAX_AGE_MS = 2 * 60_000L
        const val GEOCODE_CACHE_MS = 30_000L
    }
}
