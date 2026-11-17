package br.com.vigiauto.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import br.com.vigiauto.domain.plate.OcrDocument
import br.com.vigiauto.domain.plate.OcrElement
import br.com.vigiauto.domain.plate.OcrLine
import br.com.vigiauto.domain.plate.PlateBox
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.Closeable

/**
 * Encapsula o ML Kit Text Recognition (modelo embarcado, funciona offline).
 * O tipo Text do ML Kit não vaza para as camadas de domínio/teste.
 */
class OcrEngine : Closeable {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(input: InputImage): OcrDocument =
        recognizer.process(input).await().toDocument()

    /**
     * Segunda passada em um recorte já na orientação correta. O bitmap não é
     * armazenado nem enviado para fora do aparelho.
     */
    suspend fun recognize(bitmap: Bitmap): OcrDocument =
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().toDocument()

    private fun Text.toDocument(): OcrDocument = OcrDocument(
        lines = textBlocks.flatMap { block ->
            block.lines.map { line ->
                OcrLine(
                    text = line.text,
                    box = (line.boundingBox ?: Rect()).toPlateBox(),
                    elements = line.elements.map { element ->
                        OcrElement(
                            text = element.text,
                            box = (element.boundingBox ?: Rect()).toPlateBox()
                        )
                    }
                )
            }
        }
    )

    override fun close() {
        runCatching { recognizer.close() }
    }
}

private fun Rect.toPlateBox(): PlateBox =
    PlateBox(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
