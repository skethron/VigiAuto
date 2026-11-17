package br.com.vigiauto

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.vigiauto.alpr.OnnxPlateEngine
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Teste de fumaça do modelo ONNX embarcado.
 *
 * A imagem é sintética e não substitui o conjunto real; serve para garantir que
 * os arquivos foram empacotados corretamente e que o pipeline
 * detector → recorte → OCR funciona no dispositivo.
 */
@RunWith(AndroidJUnit4::class)
class OnnxPlateEngineTest {

    @Test
    fun detectaPlacaDaImagemDeFumaca() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.context.assets.open("alpr/test_plate.png").use {
            BitmapFactory.decodeStream(it)
        }
        requireNotNull(bitmap) { "Imagem de teste não encontrada." }

        OnnxPlateEngine(instrumentation.targetContext.assets).use { engine ->
            val detections = engine.detect(bitmap)
            assertTrue(
                "Esperava ABC1D23, veio ${detections.map { it.rawText to it.normalized }}",
                detections.any { it.normalized == "ABC1D23" }
            )
        }
        bitmap.recycle()
    }
}
