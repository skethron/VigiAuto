package br.com.vigiauto.data.photos

import java.util.ArrayDeque

data class FrameSnapshot(
    val nv21: ByteArray,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val timestamp: Long,
    val sharpness: Float = 0f
)

interface PhotoFrameSource {
    fun latestFrames(): List<FrameSnapshot>
}

/**
 * Mantém os últimos quadros analisados em memória (NV21) para escolher a
 * fotografia do veículo no momento em que uma placa é confirmada.
 *
 * O buffer é pequeno e circular: não cresce com o tempo e não armazena
 * imagens em disco antes de uma confirmação.
 */
class FrameRingBuffer(private val capacity: Int = 3) : PhotoFrameSource {

    private val lock = Any()
    private val frames = ArrayDeque<FrameSnapshot>(capacity)

    fun offer(frame: FrameSnapshot) {
        synchronized(lock) {
            frames.addLast(frame)
            while (frames.size > capacity) frames.removeFirst()
        }
    }

    override fun latestFrames(): List<FrameSnapshot> = synchronized(lock) { frames.toList() }

    fun clear() {
        synchronized(lock) { frames.clear() }
    }
}
