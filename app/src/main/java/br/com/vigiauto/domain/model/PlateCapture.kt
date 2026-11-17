package br.com.vigiauto.domain.model

import br.com.vigiauto.domain.plate.PlateBox

/**
 * Fotografia real do veículo e recorte real da placa, produzidos a partir do
 * mesmo quadro em que a placa foi confirmada.
 *
 * Os bytes são mantidos apenas em memória até a consulta terminar. Nada é
 * gravado em disco para uma leitura que não gerou registro.
 */
data class PlateCapture(
    val photoJpeg: ByteArray,
    val plateCropJpeg: ByteArray?,
    val capturedAt: Long,
    val sourceWidth: Int,
    val sourceHeight: Int,
    /** Caixa da placa nas coordenadas do quadro já na orientação vertical. */
    val plateBox: PlateBox,
    val photoQuality: PhotoQuality,
    val plateQuality: PlateQuality,
    val needsReview: Boolean,
    val cropAspectRatio: Float?
)
