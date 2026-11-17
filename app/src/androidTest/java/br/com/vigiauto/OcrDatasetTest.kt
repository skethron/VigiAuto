package br.com.vigiauto

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.vigiauto.domain.plate.CandidateExtractor
import br.com.vigiauto.ocr.OcrEngine
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Benchmark de OCR com imagens reais de veículos/placas.
 *
 * As imagens NÃO são usadas para "treinar" o ML Kit em tempo de execução — o modelo
 * é pré-treinado. Elas servem como conjunto de validação para medir a precisão do
 * pipeline (ML Kit + extração/normalização) e detectar regressões.
 *
 * Para executar:
 * 1. Copie as imagens para app/src/androidTest/assets/dataset/ com os nomes do labels.csv.
 * 2. Rode: gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class OcrDatasetTest {

    private data class Entry(val file: String, val plate: String)

    private data class Outcome(
        val entry: Entry,
        val readPlates: List<String>,
        val hit: Boolean,
        val confidence: Float
    )

    @Test
    fun benchmarkPlacasReais() = runBlocking {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets

        val entries = runCatching {
            assets.open("dataset/labels.csv").bufferedReader().useLines { lines ->
                lines.drop(1)
                    .filter { it.isNotBlank() }
                    .mapNotNull { line ->
                        val parts = line.split(",")
                        if (parts.size >= 2) {
                            Entry(parts[0].trim(), parts[1].trim().uppercase())
                        } else {
                            null
                        }
                    }
                    .toList()
            }
        }.getOrDefault(emptyList())

        fun assetExists(path: String): Boolean =
            runCatching { assets.open(path).close(); true }.getOrDefault(false)

        val available = entries.filter { assetExists("dataset/${it.file}") }
        Assume.assumeTrue(
            "Nenhuma imagem encontrada em androidTest/assets/dataset. Veja dataset/README.md.",
            available.isNotEmpty()
        )

        val engine = OcrEngine()
        val outcomes = mutableListOf<Outcome>()

        try {
            for (entry in available) {
                val bitmap = assets.open("dataset/${entry.file}").use {
                    BitmapFactory.decodeStream(it)
                }
                if (bitmap == null) {
                    outcomes += Outcome(entry, emptyList(), hit = false, confidence = 0f)
                    continue
                }

                val document = engine.recognize(InputImage.fromBitmap(bitmap, 0))
                bitmap.recycle()

                val candidates = CandidateExtractor.extract(document)
                val hit = candidates.firstOrNull { it.normalized == entry.plate }
                outcomes += Outcome(
                    entry = entry,
                    readPlates = candidates.map { it.normalized },
                    hit = hit != null,
                    confidence = hit?.confidence ?: 0f
                )
            }
        } finally {
            engine.close()
        }

        val report = buildString {
            appendLine("Benchmark OCR — ${outcomes.size} leituras")
            outcomes.forEach { outcome ->
                val status = if (outcome.hit) "OK" else "FALHA"
                appendLine(
                    "$status ${outcome.entry.file} | esperado=${outcome.entry.plate} " +
                        "| lidos=${outcome.readPlates.take(5)} | confiança=${"%.2f".format(outcome.confidence)}"
                )
            }
        }
        Log.i(TAG, report)

        val hits = outcomes.count { it.hit }
        val accuracy = hits.toFloat() / outcomes.size
        assertTrue(
            "Acurácia ${"%.0f".format(accuracy * 100)}% abaixo do mínimo de 70%.\n$report",
            accuracy >= 0.7f
        )
    }

    private companion object {
        const val TAG = "VigiAutoDataset"
    }
}
