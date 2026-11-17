package br.com.vigiauto.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import br.com.vigiauto.core.TokenCipher
import br.com.vigiauto.data.lookup.SinespCredentials
import br.com.vigiauto.data.lookup.SinespV1Protocol
import br.com.vigiauto.domain.model.AnalysisResolution
import br.com.vigiauto.domain.model.DetectionEngine
import br.com.vigiauto.domain.model.LookupMode
import br.com.vigiauto.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "vigiauto_settings")

class SettingsRepository(
    private val context: Context,
    private val tokenCipher: TokenCipher
) {

    private object Keys {
        val READING_ENABLED = booleanPreferencesKey("reading_enabled")
        val DETECTION_ENGINE = stringPreferencesKey("detection_engine")
        val SENSITIVITY = floatPreferencesKey("sensitivity")
        val ANALYSIS_FPS = intPreferencesKey("analysis_fps")
        val ANALYSIS_RESOLUTION = stringPreferencesKey("analysis_resolution")
        val REQUIRED_CONFIRMATIONS = intPreferencesKey("required_confirmations")
        val MOTION_MODE = booleanPreferencesKey("motion_mode")
        val CAMERA_STABILIZATION = booleanPreferencesKey("camera_stabilization")
        val MIN_REPEAT_QUERY_MINUTES = intPreferencesKey("min_repeat_query_minutes")
        val POPUP_DURATION_SECONDS = intPreferencesKey("popup_duration_seconds")
        val DUPLICATE_ALERT_SUPPRESSION_SECONDS = intPreferencesKey("duplicate_alert_suppression_seconds")
        val POPUP_ENABLED = booleanPreferencesKey("popup_enabled")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val MAX_RECORDS = intPreferencesKey("max_records")
        val PHOTO_STORAGE_LIMIT_MB = intPreferencesKey("photo_storage_limit_mb")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LOOKUP_MODE = stringPreferencesKey("lookup_mode")
        val HTTP_BASE_URL = stringPreferencesKey("http_base_url")
        val HTTP_PATH = stringPreferencesKey("http_path")
        val HTTP_PLATE_PARAM = stringPreferencesKey("http_plate_param")
        val HTTP_TOKEN = stringPreferencesKey("http_token_encrypted")
        val SINESP_TOKEN = stringPreferencesKey("sinesp_token_encrypted")
        val SINESP_GEO = stringPreferencesKey("sinesp_geo")
        val SINESP_UNIQUE_ID = stringPreferencesKey("sinesp_unique_id")
        val SINESP_DEFAULT_MIGRATED_V150 = booleanPreferencesKey("sinesp_default_migrated_v150")
        val LAST_CAMERA_LENS_INDEX = intPreferencesKey("last_camera_lens_index")
    }

    private val legacyDadosApiTokenKey =
        stringPreferencesKey("dados_api_token_encrypted")

    val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { preferences -> preferences.toSettings() }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            readingEnabled = this[Keys.READING_ENABLED] ?: defaults.readingEnabled,
            detectionEngine = enumOrElse(this[Keys.DETECTION_ENGINE], defaults.detectionEngine),
            sensitivity = (this[Keys.SENSITIVITY] ?: defaults.sensitivity).coerceIn(0.3f, 0.99f),
            analysisFps = (this[Keys.ANALYSIS_FPS] ?: defaults.analysisFps).coerceIn(1, 15),
            analysisResolution = enumOrElse(this[Keys.ANALYSIS_RESOLUTION], defaults.analysisResolution),
            requiredConfirmations = (this[Keys.REQUIRED_CONFIRMATIONS] ?: defaults.requiredConfirmations).coerceIn(1, 5),
            motionMode = this[Keys.MOTION_MODE] ?: defaults.motionMode,
            cameraStabilization = this[Keys.CAMERA_STABILIZATION] ?: defaults.cameraStabilization,
            minRepeatQueryMinutes = (this[Keys.MIN_REPEAT_QUERY_MINUTES] ?: defaults.minRepeatQueryMinutes).coerceIn(0, 1440),
            popupDurationSeconds = (this[Keys.POPUP_DURATION_SECONDS] ?: defaults.popupDurationSeconds).coerceIn(2, 30),
            duplicateAlertSuppressionSeconds = (this[Keys.DUPLICATE_ALERT_SUPPRESSION_SECONDS] ?: defaults.duplicateAlertSuppressionSeconds).coerceIn(10, 900),
            popupEnabled = this[Keys.POPUP_ENABLED] ?: defaults.popupEnabled,
            vibrationEnabled = this[Keys.VIBRATION_ENABLED] ?: defaults.vibrationEnabled,
            maxRecords = (this[Keys.MAX_RECORDS] ?: defaults.maxRecords).coerceIn(10, 100_000),
            photoStorageLimitMb = (this[Keys.PHOTO_STORAGE_LIMIT_MB] ?: defaults.photoStorageLimitMb).coerceIn(50, 5_000),
            retentionDays = (this[Keys.RETENTION_DAYS] ?: defaults.retentionDays).coerceIn(0, 3_650),
            themeMode = enumOrElse(this[Keys.THEME_MODE], ThemeMode.SYSTEM),
            lookupMode = enumOrElse(this[Keys.LOOKUP_MODE], defaults.lookupMode),
            httpBaseUrl = this[Keys.HTTP_BASE_URL] ?: defaults.httpBaseUrl,
            httpPath = this[Keys.HTTP_PATH] ?: defaults.httpPath,
            httpPlateParam = this[Keys.HTTP_PLATE_PARAM] ?: defaults.httpPlateParam,
            sinespGeo = this[Keys.SINESP_GEO]?.takeIf { it.isNotBlank() } ?: defaults.sinespGeo,
            hasHttpToken = !this[Keys.HTTP_TOKEN].isNullOrBlank(),
            hasSinespToken = !this[Keys.SINESP_TOKEN].isNullOrBlank(),
            lastCameraLensIndex = (this[Keys.LAST_CAMERA_LENS_INDEX] ?: 0).coerceAtLeast(0)
        )
    }

    suspend fun setReadingEnabled(value: Boolean) = edit { it[Keys.READING_ENABLED] = value }
    suspend fun setDetectionEngine(value: DetectionEngine) = edit { it[Keys.DETECTION_ENGINE] = value.name }
    suspend fun setSensitivity(value: Float) = edit { it[Keys.SENSITIVITY] = value.coerceIn(0.3f, 0.99f) }
    suspend fun setAnalysisFps(value: Int) = edit { it[Keys.ANALYSIS_FPS] = value.coerceIn(1, 15) }
    suspend fun setAnalysisResolution(value: AnalysisResolution) = edit { it[Keys.ANALYSIS_RESOLUTION] = value.name }
    suspend fun setRequiredConfirmations(value: Int) = edit { it[Keys.REQUIRED_CONFIRMATIONS] = value.coerceIn(1, 5) }
    suspend fun setMotionMode(value: Boolean) = edit { it[Keys.MOTION_MODE] = value }
    suspend fun setCameraStabilization(value: Boolean) = edit { it[Keys.CAMERA_STABILIZATION] = value }
    suspend fun setMinRepeatQueryMinutes(value: Int) = edit { it[Keys.MIN_REPEAT_QUERY_MINUTES] = value.coerceIn(0, 1440) }
    suspend fun setPopupDurationSeconds(value: Int) = edit { it[Keys.POPUP_DURATION_SECONDS] = value.coerceIn(2, 30) }
    suspend fun setDuplicateAlertSuppressionSeconds(value: Int) = edit { it[Keys.DUPLICATE_ALERT_SUPPRESSION_SECONDS] = value.coerceIn(10, 900) }
    suspend fun setPopupEnabled(value: Boolean) = edit { it[Keys.POPUP_ENABLED] = value }
    suspend fun setVibrationEnabled(value: Boolean) = edit { it[Keys.VIBRATION_ENABLED] = value }
    suspend fun setMaxRecords(value: Int) = edit { it[Keys.MAX_RECORDS] = value.coerceIn(10, 100_000) }
    suspend fun setPhotoStorageLimitMb(value: Int) = edit { it[Keys.PHOTO_STORAGE_LIMIT_MB] = value.coerceIn(50, 5_000) }
    suspend fun setRetentionDays(value: Int) = edit { it[Keys.RETENTION_DAYS] = value.coerceIn(0, 3_650) }
    suspend fun setThemeMode(value: ThemeMode) = edit { it[Keys.THEME_MODE] = value.name }
    suspend fun setLookupMode(value: LookupMode) = edit { it[Keys.LOOKUP_MODE] = value.name }
    suspend fun setHttpBaseUrl(value: String) = edit { it[Keys.HTTP_BASE_URL] = value.trim() }
    suspend fun setHttpPath(value: String) = edit { it[Keys.HTTP_PATH] = value.trim() }
    suspend fun setHttpPlateParam(value: String) = edit { it[Keys.HTTP_PLATE_PARAM] = value.trim() }
    suspend fun setLastCameraLensIndex(value: Int) = edit { it[Keys.LAST_CAMERA_LENS_INDEX] = value.coerceAtLeast(0) }

    suspend fun setHttpToken(token: String?) {
        val normalized = token?.trim()?.takeIf { it.isNotEmpty() }
        val encrypted = normalized?.let { tokenCipher.encrypt(it) }
        edit { preferences ->
            if (encrypted == null) preferences.remove(Keys.HTTP_TOKEN)
            else preferences[Keys.HTTP_TOKEN] = encrypted
        }
    }

    suspend fun httpToken(): String? {
        val encrypted = context.dataStore.data.first()[Keys.HTTP_TOKEN] ?: return null
        return tokenCipher.decrypt(encrypted)
    }

    suspend fun setSinespToken(token: String?) {
        val normalized = SinespV1Protocol.normalizeToken(token)
        val encrypted = normalized?.let { tokenCipher.encrypt(it) }
        edit { preferences ->
            if (encrypted == null) preferences.remove(Keys.SINESP_TOKEN)
            else preferences[Keys.SINESP_TOKEN] = encrypted
        }
    }

    suspend fun setSinespGeo(value: String) {
        edit { it[Keys.SINESP_GEO] = value.trim() }
    }

    /**
     * Lê token, localização e identificador estável em uma única passagem.
     * O token é decifrado pelo Keystore; o UniqueId é gerado uma vez e
     * reutilizado nas consultas seguintes.
     */
    suspend fun sinespCredentials(): SinespCredentials {
        val preferences = context.dataStore.data.first()
        val encrypted = preferences[Keys.SINESP_TOKEN]
        val token = encrypted?.let { tokenCipher.decrypt(it) }
        val geo = preferences[Keys.SINESP_GEO]
            ?.takeIf { it.isNotBlank() }
            ?: AppSettings().sinespGeo
        val uniqueId = preferences[Keys.SINESP_UNIQUE_ID]
            ?.takeIf { it.isNotBlank() }
            ?: generateSinespUniqueId()

        return SinespCredentials(token = token, geo = geo, uniqueId = uniqueId)
    }

    private suspend fun generateSinespUniqueId(): String {
        val generated = UUID.randomUUID().toString()
        edit { it[Keys.SINESP_UNIQUE_ID] = generated }
        return generated
    }

    /**
     * Migração única da 1.5.0: instalações antigas guardavam a Consulta Simples
     * (antigo modo Real) como padrão. Como o Sinesp v1 passa a ser a fonte
     * principal desta versão, troca apenas o antigo padrão (ou a ausência de
     * valor) uma vez. Quem escolher outro modo depois não é afetado nas
     * próximas aberturas.
     */
    suspend fun migrateToSinespDefaultIfNeeded() {
        edit { preferences ->
            if (preferences[Keys.SINESP_DEFAULT_MIGRATED_V150] != true) {
                val current = preferences[Keys.LOOKUP_MODE]
                if (current == null || current == LookupMode.REMOTE.name) {
                    preferences[Keys.LOOKUP_MODE] = LookupMode.SINESP.name
                }
                preferences[Keys.SINESP_DEFAULT_MIGRATED_V150] = true
            }
        }
    }

    /**
     * Remove o token cifrado da antiga integração Dados API. A integração foi
     * removida; manter o segredo no DataStore não teria utilidade e ampliaria
     * desnecessariamente a retenção de dados.
     */
    suspend fun clearLegacyDadosApiToken() = edit { it.remove(legacyDadosApiTokenKey) }

    /**
     * Correção confirmada pela consulta para uma leitura ambígua de OCR.
     * Persistida para evitar recriar a placa errada depois de reiniciar o app.
     */
    suspend fun plateCorrection(plate: String): String? =
        context.dataStore.data.first()[plateCorrectionKey(plate)]

    suspend fun setPlateCorrection(wrong: String, right: String) {
        if (wrong == right) return
        edit { it[plateCorrectionKey(wrong)] = right }
    }

    private fun plateCorrectionKey(plate: String) =
        stringPreferencesKey("plate_correction_$plate")

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private inline fun <reified T : Enum<T>> enumOrElse(value: String?, fallback: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
}
