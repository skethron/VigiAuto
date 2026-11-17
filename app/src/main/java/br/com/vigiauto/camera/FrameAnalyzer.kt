package br.com.vigiauto.camera

import androidx.camera.core.ImageAnalysis
import java.io.Closeable

/**
 * Contrato comum dos analisadores de quadro. A tela principal pode alternar
 * entre o detector visual ONNX e o pipeline ML Kit sem conhecer detalhes.
 */
interface FrameAnalyzer : ImageAnalysis.Analyzer, Closeable {
    fun resetConfirmation()
}
