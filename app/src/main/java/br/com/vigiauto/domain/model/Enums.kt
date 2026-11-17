package br.com.vigiauto.domain.model

enum class QueryStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    NOT_FOUND,
    CONNECTION_FAILURE,
    AUTH_ERROR,
    RATE_LIMITED,
    INVALID_RESPONSE,
    INSUFFICIENT_CREDITS,
    INCOMPLETE,
    UNAVAILABLE;

    val isTerminal: Boolean
        get() = this == COMPLETED || this == INCOMPLETE || this == NOT_FOUND ||
            this == UNAVAILABLE

    val isFailure: Boolean
        get() = this == CONNECTION_FAILURE || this == AUTH_ERROR ||
            this == RATE_LIMITED || this == INVALID_RESPONSE ||
            this == INSUFFICIENT_CREDITS || this == UNAVAILABLE
}

enum class RestrictionStatus {
    CONFIRMED,
    NONE_IDENTIFIED,
    INCONCLUSIVE,
    UNKNOWN
}

enum class PhotoQuality {
    GOOD,
    LIMITED,
    UNAVAILABLE
}

/** Qualidade do recorte real da placa exibido na miniatura. */
enum class PlateQuality {
    GOOD,
    LOW,
    UNAVAILABLE
}

enum class LookupMode {
    REMOTE,
    SINESP,
    HTTP,
    DEMO
}

/**
 * Motor de localização/leitura das placas.
 *
 * [ONNX] usa o detector visual YOLOv9 e o OCR CCT embarcados. [ML_KIT] mantém
 * o pipeline anterior (texto no quadro inteiro) como contingência.
 */
enum class DetectionEngine {
    ONNX,
    ML_KIT
}

/**
 * Resolução enviada ao ImageAnalysis. Resoluções menores reduzem o custo do OCR
 * e aceleram a resposta; maiores ajudam em placas pequenas/distantes.
 */
enum class AnalysisResolution(val width: Int, val height: Int) {
    FAST(960, 540),
    BALANCED(1280, 720),
    HIGH(1920, 1080)
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}
