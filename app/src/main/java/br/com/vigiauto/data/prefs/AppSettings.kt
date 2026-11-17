package br.com.vigiauto.data.prefs

import br.com.vigiauto.domain.model.AnalysisResolution
import br.com.vigiauto.domain.model.DetectionEngine
import br.com.vigiauto.domain.model.LookupMode
import br.com.vigiauto.domain.model.ThemeMode

data class AppSettings(
    val readingEnabled: Boolean = true,
    val detectionEngine: DetectionEngine = DetectionEngine.ONNX,
    val sensitivity: Float = 0.70f,
    // 12 quadros/s reduz a espera entre a placa entrar no enquadramento e a
    // confirmação. O detector/OCR continuam limitados pelo tempo real de
    // processamento; a cadência só evita descartar quadros úteis.
    val analysisFps: Int = 12,
    val analysisResolution: AnalysisResolution = AnalysisResolution.BALANCED,
    val requiredConfirmations: Int = 2,
    val motionMode: Boolean = true,
    val cameraStabilization: Boolean = true,
    val minRepeatQueryMinutes: Int = 30,
    val popupDurationSeconds: Int = 8,
    val duplicateAlertSuppressionSeconds: Int = 60,
    val popupEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val maxRecords: Int = 1000,
    val photoStorageLimitMb: Int = 500,
    val retentionDays: Int = 90,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    // A 1.5.0 entrega o Sinesp v1 como fonte principal. A Consulta Simples
    // (API Consulta Placa) continua disponível nas configurações.
    val lookupMode: LookupMode = LookupMode.SINESP,
    val httpBaseUrl: String = "https://placa.costaw.com.br",
    val httpPath: String = "/api",
    val httpPlateParam: String = "placa",
    // Ponto neutro usado quando o usuário não informa a localização do Sinesp.
    val sinespGeo: String = "-47.8825,-15.7942",
    val lastCameraLensIndex: Int = 0,
    val hasHttpToken: Boolean = false,
    val hasSinespToken: Boolean = false
) {
    val analysisIntervalMs: Long get() = (1000L / analysisFps.coerceIn(1, 15))
}
