package br.com.vigiauto.domain.model

import br.com.vigiauto.domain.plate.PlateBox

/**
 * Candidato de placa produzido pela camada de OCR.
 *
 * [candidatesInFrame] indica quantas placas distintas foram lidas no mesmo quadro e é
 * usado para sinalizar qualidade limitada da fotografia (duas placas na mesma imagem).
 */
data class PlateCandidate(
    val rawText: String,
    val normalized: String,
    val confidence: Float,
    val box: PlateBox = PlateBox.EMPTY,
    val lineCount: Int = 1,
    val candidatesInFrame: Int = 1,
    val detectorConfidence: Float = confidence,
    val ocrConfidence: Float = confidence,
    /**
     * Fotografia real e recorte real produzidos no mesmo quadro da confirmação.
     * O ONNX preenche este campo; o caminho ML Kit pode usar o quadro do buffer
     * como contingência.
     */
    val capture: PlateCapture? = null
)
