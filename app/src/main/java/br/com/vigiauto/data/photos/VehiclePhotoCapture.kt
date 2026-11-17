package br.com.vigiauto.data.photos

import android.graphics.Bitmap
import br.com.vigiauto.core.ImageEncoding
import br.com.vigiauto.domain.model.PhotoQuality
import br.com.vigiauto.domain.model.PlateCapture
import br.com.vigiauto.domain.model.PlateQuality
import br.com.vigiauto.domain.plate.PlateBox
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Produz a fotografia quadrada real do veículo e o recorte colorido da placa.
 *
 * Não há geração, reconstrução ou substituição de caracteres. O recorte usa
 * exatamente a caixa devolvida pelo YOLO; a única transformação é a ampliação
 * proporcional com interpolação de bitmap.
 */
object VehiclePhotoCapture {

    private const val MAX_PHOTO_SIDE = 1080f
    private const val PHOTO_JPEG_QUALITY = 88
    private const val PLATE_JPEG_QUALITY = 93
    private const val TARGET_PLATE_HEIGHT = 240f
    private const val MAX_PLATE_WIDTH = 1200f
    private const val MIN_PLATE_SCALE = 1f
    private const val MAX_PLATE_SCALE = 6f

    fun create(
        source: Bitmap,
        plateBox: PlateBox?,
        capturedAt: Long,
        candidatesInFrame: Int,
        detectorConfidence: Float,
        ocrConfidence: Float,
        textMismatch: Boolean = false
    ): PlateCapture? {
        if (source.isRecycled || source.width <= 0 || source.height <= 0) return null

        val square = VehiclePhotoGeometry.squareAround(
            box = plateBox ?: PlateBox.EMPTY,
            frameWidth = source.width,
            frameHeight = source.height
        ) ?: return null
        val plateRect = plateBox?.let {
            VehiclePhotoGeometry.plateRect(it, source.width, source.height)
        }

        var vehicleCrop: Bitmap? = null
        var vehiclePhoto: Bitmap? = null
        var plateCrop: Bitmap? = null
        var plateThumb: Bitmap? = null
        try {
            val squareBitmap = runCatching {
                Bitmap.createBitmap(source, square.left, square.top, square.width, square.height)
            }.getOrNull() ?: return null
            vehicleCrop = squareBitmap
            val photo = scaleDownIfNeeded(squareBitmap, MAX_PHOTO_SIDE)
            vehiclePhoto = photo
            val photoJpeg = encodeJpeg(photo, PHOTO_JPEG_QUALITY) ?: return null

            var plateJpeg: ByteArray? = null
            var cropAspectRatio: Float? = null
            var cropSharpness: Float? = null
            val thumbRect = plateBox?.let {
                VehiclePhotoGeometry.plateThumbRect(
                    box = it,
                    square = square,
                    photoWidth = photo.width,
                    photoHeight = photo.height
                )
            }
            if (thumbRect != null) {
                // A miniatura é tirada da própria fotografia do veículo, com
                // margem curta e enquadramento exato da caixa da placa.
                plateCrop = runCatching {
                    Bitmap.createBitmap(
                        photo,
                        thumbRect.left,
                        thumbRect.top,
                        thumbRect.width,
                        thumbRect.height
                    )
                }.getOrNull()
                val crop = plateCrop
                if (crop != null) {
                    cropAspectRatio = crop.width.toFloat() / crop.height.toFloat()
                    cropSharpness = normalizedLaplacian(crop)
                    plateThumb = upscalePlate(crop)
                    plateJpeg = encodeJpeg(plateThumb, PLATE_JPEG_QUALITY)
                }
            }

            val cropAvailable = plateJpeg != null
            val assessment = PlateQualityPolicy.assess(
                detectorConfidence = detectorConfidence,
                ocrConfidence = ocrConfidence,
                plateWidthPx = plateRect?.width?.toFloat() ?: 0f,
                cropSharpness = cropSharpness,
                candidatesInFrame = candidatesInFrame,
                cropAvailable = cropAvailable,
                textMismatch = textMismatch
            )

            return PlateCapture(
                photoJpeg = photoJpeg,
                plateCropJpeg = plateJpeg,
                capturedAt = capturedAt,
                sourceWidth = source.width,
                sourceHeight = source.height,
                plateBox = plateBox ?: PlateBox.EMPTY,
                photoQuality = if (cropAvailable) {
                    assessment.photoQuality
                } else {
                    PhotoQuality.LIMITED
                },
                plateQuality = if (cropAvailable) {
                    assessment.plateQuality
                } else {
                    PlateQuality.UNAVAILABLE
                },
                needsReview = assessment.needsReview,
                cropAspectRatio = cropAspectRatio
            )
        } catch (_: Throwable) {
            return null
        } finally {
            if (plateThumb != null && plateThumb !== plateCrop) {
                runCatching { plateThumb.recycle() }
            }
            runCatching { plateCrop?.recycle() }
            if (vehiclePhoto != null && vehiclePhoto !== vehicleCrop) {
                runCatching { vehiclePhoto.recycle() }
            }
            runCatching { vehicleCrop?.recycle() }
        }
    }

    /**
     * Contingência para o pipeline ML Kit, que não entrega o bitmap já girado
     * ao chamador. O NV21 é decodificado uma única vez e a caixa continua sendo
     * a mesma leitura que confirmou a placa.
     */
    fun create(
        frame: FrameSnapshot,
        plateBox: PlateBox?,
        capturedAt: Long,
        candidatesInFrame: Int,
        detectorConfidence: Float,
        ocrConfidence: Float,
        textMismatch: Boolean = false
    ): PlateCapture? {
        val bitmap = ImageEncoding.decodeUprightBitmap(
            nv21 = frame.nv21,
            width = frame.width,
            height = frame.height,
            rotationDegrees = frame.rotationDegrees
        ) ?: return null
        return try {
            create(
                source = bitmap,
                plateBox = plateBox,
                capturedAt = capturedAt,
                candidatesInFrame = candidatesInFrame,
                detectorConfidence = detectorConfidence,
                ocrConfidence = ocrConfidence,
                textMismatch = textMismatch
            )
        } finally {
            runCatching { bitmap.recycle() }
        }
    }

    private fun scaleDownIfNeeded(source: Bitmap, maxSide: Float): Bitmap {
        val largest = max(source.width, source.height).toFloat()
        if (largest <= maxSide) return source
        val scale = maxSide / largest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * scale).roundToInt().coerceAtLeast(1),
            (source.height * scale).roundToInt().coerceAtLeast(1),
            true
        )
    }

    private fun upscalePlate(source: Bitmap): Bitmap {
        val scale = min(TARGET_PLATE_HEIGHT / source.height, MAX_PLATE_WIDTH / source.width)
            .coerceIn(MIN_PLATE_SCALE, MAX_PLATE_SCALE)
        if (scale <= 1.01f) return source
        return Bitmap.createScaledBitmap(
            source,
            (source.width * scale).roundToInt().coerceAtLeast(1),
            (source.height * scale).roundToInt().coerceAtLeast(1),
            true
        )
    }

    private fun encodeJpeg(bitmap: Bitmap, quality: Int): ByteArray? = runCatching {
        val output = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) return null
        output.toByteArray()
    }.getOrNull()

    /**
     * Nitidez relativa do recorte, usada somente para sinalizar baixa qualidade.
     * O valor não interfere nos pixels exibidos.
     */
    private fun normalizedLaplacian(bitmap: Bitmap): Float? {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 8 || height < 4) return null

        val pixels = IntArray(width * height)
        runCatching { bitmap.getPixels(pixels, 0, width, 0, 0, width, height) }
            .getOrElse { return null }

        val step = max(1, min(width, height) / 48)
        var sum = 0.0
        var samples = 0
        var y = step
        while (y < height - step) {
            var x = step
            while (x < width - step) {
                val center = luminance(pixels[y * width + x])
                val left = luminance(pixels[y * width + x - step])
                val right = luminance(pixels[y * width + x + step])
                val top = luminance(pixels[(y - step) * width + x])
                val bottom = luminance(pixels[(y + step) * width + x])
                val laplacian = abs(4 * center - left - right - top - bottom)
                sum += laplacian
                samples += 1
                x += step
            }
            y += step
        }
        if (samples == 0) return null
        return (sum / samples / 255.0).toFloat()
    }

    private fun luminance(color: Int): Int {
        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF
        return (red * 77 + green * 150 + blue * 29) shr 8
    }
}
