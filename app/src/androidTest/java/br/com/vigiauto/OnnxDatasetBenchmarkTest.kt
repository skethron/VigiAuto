package br.com.vigiauto

import android.graphics.BitmapFactory
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.vigiauto.alpr.OnnxPlateEngine
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * Benchmark do pipeline detector-primeiro com as imagens reais do dataset.
 *
 * As imagens não são usadas para treinar nada em tempo de execução: o modelo é
 * pré-treinado. O teste mede, por imagem:
 * - placas esperadas e placas detectadas;
 * - recall, precisão e falsos positivos;
 * - latência de parede e tempo de CPU da thread;
 * - PSS aproximado antes/depois.
 *
 * Ele é ignorado enquanto as imagens não estiverem em
 * `app/src/androidTest/assets/dataset/`. Para rodar:
 * `gradlew connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class OnnxDatasetBenchmarkTest {

    private data class Label(val file: String, val plate: String)

    private data class ImageResult(
        val file: String,
        val expected: Set<String>,
        val detected: Set<String>,
        val latencyMs: Long,
        val cpuMs: Long
    )

    @Test
    fun benchmarkDatasetReal() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val testAssets = instrumentation.context.assets
        val appAssets = instrumentation.targetContext.assets

        val labels = runCatching {
            testAssets.open("dataset/labels.csv").bufferedReader().useLines { lines ->
                lines.drop(1).mapNotNull { line ->
                    val parts = line.split(",")
                    if (parts.size >= 2) Label(parts[0].trim(), parts[1].trim().uppercase())
                    else null
                }.toList()
            }
        }.getOrDefault(emptyList())

        val images = labels.map { it.file }.distinct().filter { file ->
            runCatching { testAssets.open("dataset/$file").close(); true }.getOrDefault(false)
        }
        if (images.isEmpty()) {
            Assume.assumeTrue(
                "Dataset real ausente. Coloque as imagens em app/src/androidTest/assets/dataset/.",
                false
            )
        }

        val expectedByFile = labels.groupBy { it.file }
            .mapValues { (_, entries) -> entries.map { it.plate }.toSet() }

        val memoryBefore = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        val results = mutableListOf<ImageResult>()

        OnnxPlateEngine(appAssets).use { engine ->
            // Aquece as sessões ONNX antes de medir latência.
            runCatching {
                testAssets.open("alpr/test_plate.png").use { BitmapFactory.decodeStream(it) }
            }.getOrNull()?.let { warmup ->
                engine.detect(warmup)
                warmup.recycle()
            }

            for (file in images) {
                val bitmap = testAssets.open("dataset/$file").use { BitmapFactory.decodeStream(it) }
                if (bitmap == null) {
                    Log.w(TAG, "Não foi possível decodificar $file")
                    continue
                }

                val wallStart = SystemClock.elapsedRealtimeNanos()
                val cpuStart = Debug.threadCpuTimeNanos()
                val detections = engine.detect(bitmap)
                val cpuMs = (Debug.threadCpuTimeNanos() - cpuStart) / 1_000_000
                val latencyMs = (SystemClock.elapsedRealtimeNanos() - wallStart) / 1_000_000
                bitmap.recycle()

                val expected = expectedByFile[file].orEmpty()
                val detected = detections.mapNotNull { it.normalized }.toSet()
                results.add(ImageResult(file, expected, detected, latencyMs, cpuMs))
                Log.i(
                    TAG,
                    String.format(
                        Locale.US,
                        "%s esperado=%s detectado=%s latencia=%dms cpu=%dms",
                        file, expected, detected, latencyMs, cpuMs
                    )
                )
            }
        }

        val memoryAfter = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }

        val expectedTotal = results.sumOf { it.expected.size }
        val detectedTotal = results.sumOf { it.detected.size }
        val matchedTotal = results.sumOf { it.expected.intersect(it.detected).size }
        val falsePositives = detectedTotal - matchedTotal
        val recall = if (expectedTotal == 0) 0.0 else matchedTotal.toDouble() / expectedTotal
        val precision = if (detectedTotal == 0) 0.0 else matchedTotal.toDouble() / detectedTotal
        val avgLatency = results.map { it.latencyMs }.average()
        val avgCpu = results.map { it.cpuMs }.average()

        val summary = String.format(
            Locale.US,
            "imagens=%d esperadas=%d detectadas=%d acertos=%d recall=%.3f precisao=%.3f falsosPositivos=%d latenciaMedia=%.1fms cpuMedio=%.1fms pssAntes=%dKB pssDepois=%dKB",
            results.size,
            expectedTotal,
            detectedTotal,
            matchedTotal,
            recall,
            precision,
            falsePositives,
            avgLatency,
            avgCpu,
            memoryBefore.totalPss,
            memoryAfter.totalPss
        )
        Log.i(TAG, summary)

        val report = buildString {
            appendLine("arquivo,esperadas,detectadas,acertos,latencia_ms,cpu_ms")
            results.forEach { result ->
                val matched = result.expected.intersect(result.detected).size
                appendLine(
                    "${result.file},\"${result.expected.joinToString("|")}\"," +
                        "\"${result.detected.joinToString("|")}\",$matched," +
                        "${result.latencyMs},${result.cpuMs}"
                )
            }
            appendLine("resumo,\"$summary\"")
        }
        val reportFile = File(
            instrumentation.targetContext.getExternalFilesDir(null),
            "alpr-benchmark.csv"
        )
        runCatching { reportFile.writeText(report) }
        Log.i(TAG, "Relatório: ${reportFile.absolutePath}")

        assertTrue("O benchmark não processou nenhuma imagem.", results.isNotEmpty())
        assertTrue(
            "Nenhuma placa foi detectada no dataset; verifique modelos e limiares.",
            matchedTotal > 0
        )
    }

    private companion object {
        const val TAG = "VigiAutoAlprBenchmark"
    }
}
