package br.com.vigiauto.ui.details

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import br.com.vigiauto.domain.model.RestrictionStatus
import br.com.vigiauto.domain.model.VehicleRecord
import br.com.vigiauto.domain.model.normalizeOccurrenceDates
import br.com.vigiauto.ui.components.formatDateTime
import br.com.vigiauto.ui.components.label
import br.com.vigiauto.ui.components.noTheftAlertMessageOrNull
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.max

/**
 * Gera localmente o card JPEG compartilhado.
 *
 * O card mantém o cabeçalho, a fotografia, os dados da consulta e o rodapé.
 * A fotografia quadrada recebe a miniatura real do recorte da placa no canto
 * inferior esquerdo; o status "Consulta concluída" não é mais desenhado.
 */
class ShareCardGenerator(private val context: Context) {

    private val width = 1080
    private val margin = 56f
    private val contentWidth = width - margin * 2

    private val backgroundColor = Color.parseColor("#0B1220")
    private val surfaceColor = Color.parseColor("#111C31")
    private val accentColor = Color.parseColor("#2F80ED")
    private val labelColor = Color.parseColor("#8EA9C9")
    private val primaryText = Color.WHITE
    private val dividerColor = Color.parseColor("#22314A")

    fun generate(
        record: VehicleRecord,
        photoFile: File?,
        plateCropFile: File? = null
    ): Uri? = runCatching {
        val directory = File(context.cacheDir, "shared").apply { mkdirs() }
        cleanup(directory)

        val photo = photoFile?.let(::decodePhoto)
        val plateCrop = plateCropFile?.let(::decodePhoto)
        val card = try {
            render(record, photo, plateCrop)
        } finally {
            photo?.recycle()
            plateCrop?.recycle()
        }

        val safePlate = record.displayPlate.filter { it.isLetterOrDigit() }.ifBlank { "veiculo" }
        val file = File(directory, "VigiAuto_${safePlate}_${System.currentTimeMillis()}.jpg")
        try {
            FileOutputStream(file).use { output ->
                if (!card.compress(Bitmap.CompressFormat.JPEG, 92, output)) {
                    throw IllegalStateException("Não foi possível codificar o JPEG.")
                }
            }
        } finally {
            card.recycle()
        }
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }.getOrNull()

    private fun render(
        record: VehicleRecord,
        photo: Bitmap?,
        plateCrop: Bitmap?
    ): Bitmap {
        val rows = buildRows(record)
        val photoHeight = if (photo != null) 600f else 0f
        val probePaint = valueTextPaint()
        val rowsHeight = rows.fold(0f) { total, row ->
            total + measureValue(row.value, probePaint).toFloat() + 64f
        }
        val height = max(1150, (150f + photoHeight + 170f + rowsHeight + 110f + 70f).toInt())

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    width.toFloat(),
                    height.toFloat(),
                    backgroundColor,
                    Color.parseColor("#16243B"),
                    Shader.TileMode.CLAMP
                )
            }
        )

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 46f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        canvas.drawText("VigiAuto", margin, 62f, brandPaint)

        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor
            textSize = 25f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val queryTimestamp = record.queryTimestamp ?: record.lastSeenTimestamp
        canvas.drawText(
            "CONSULTA VEICULAR • ${formatDateTime(queryTimestamp)}",
            margin,
            38f + 62f,
            subtitlePaint
        )

        val dividerY = 62f + 96f
        canvas.drawRect(
            margin,
            dividerY,
            width - margin,
            dividerY + 1f,
            Paint().apply { color = dividerColor }
        )

        val y: Float
        if (photo != null) {
            val photoRect = RectF(margin, dividerY + 30f, width - margin, dividerY + 30f + 540f)
            drawPhoto(canvas, photo, photoRect)
            if (plateCrop != null) {
                drawPlateCrop(canvas, plateCrop, photoRect)
            }
            y = photoRect.bottom + 42f
        } else {
            y = dividerY + 34f
        }

        val plateRect = RectF(margin, y, width - margin, y + 170f)
        canvas.drawRoundRect(
            plateRect,
            28f,
            28f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = surfaceColor
                style = Paint.Style.FILL
            }
        )
        canvas.drawRoundRect(
            plateRect,
            28f,
            28f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentColor
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
        )

        val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryText
            textSize = 86f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        val plate = record.displayPlate
        val measuredPlateWidth = platePaint.measureText(plate)
        val maxPlateWidth = contentWidth - 72f
        if (measuredPlateWidth > maxPlateWidth && measuredPlateWidth > 0f) {
            platePaint.textSize *= maxPlateWidth / measuredPlateWidth
        }
        canvas.drawText(plate, width / 2f, y + 108f, platePaint)

        var rowY = plateRect.bottom + 46f
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor
            textSize = 24f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                canvas.drawRect(
                    margin,
                    rowY - 18f,
                    width - margin,
                    rowY - 17f,
                    Paint().apply { color = dividerColor }
                )
            }
            canvas.drawText(
                row.label.uppercase(Locale.getDefault()),
                margin,
                rowY + 20f,
                labelPaint
            )
            val layout = valueLayout(row.value, probePaint)
            canvas.save()
            canvas.translate(margin, rowY + 42f)
            layout.draw(canvas)
            canvas.restore()
            rowY += layout.height + 64f
        }

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor
            textSize = 23f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        canvas.drawText(
            "Gerado pelo VigiAuto • Consulta local, sem envio de dados",
            margin,
            height - 52f,
            footerPaint
        )
        return bitmap
    }

    private fun buildRows(record: VehicleRecord): List<CardRow> {
        val category = record.nmtStatus?.categoryLabel()
            ?: record.vehicleCategory?.takeIf { it.isNotBlank() }
            ?: "Não informada"

        val noTheft = noTheftAlertMessageOrNull(
            record.queryStatus,
            record.restrictionStatus,
            record.restrictionDescription,
            record.sourceName
        )
        val restriction = noTheft ?: run {
            val description = normalizeOccurrenceDates(record.restrictionDescription)
            if (record.restrictionStatus == RestrictionStatus.CONFIRMED && !description.isNullOrBlank()) {
                description
            } else {
                record.restrictionStatus.label()
            }
        }

        val location = record.locationFullLine ?: "Não capturada"
        val query = buildString {
            append(formatDateTime(record.queryTimestamp ?: record.lastSeenTimestamp))
            record.sourceName?.takeIf { it.isNotBlank() }?.let {
                append(" • ").append(it)
            }
        }
        val confidence = String.format(
            Locale.getDefault(),
            "%.0f%%",
            record.recognitionConfidence * 100f
        )

        return listOf(
            CardRow("Veículo", record.vehicleSummary ?: "Dados do veículo indisponíveis"),
            CardRow("Categoria", category),
            CardRow("Situação", restriction),
            CardRow("Local da consulta", location),
            CardRow("Consulta", query),
            CardRow("Confiança", confidence)
        )
    }

    private fun valueTextPaint(): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryText
        textSize = 38f
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        isSubpixelText = true
    }

    private fun valueLayout(value: String, paint: TextPaint): StaticLayout =
        StaticLayout.Builder.obtain(value, 0, value.length, paint, contentWidth.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(2f, 1.08f)
            .build()

    private fun measureValue(value: String, paint: TextPaint): Int =
        valueLayout(value, paint).height

    private fun drawPhoto(canvas: Canvas, bitmap: Bitmap, destination: RectF) {
        val path = Path().apply {
            addRoundRect(destination, 28f, 28f, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(path)
        val source = centerCropRect(bitmap, destination)
        canvas.drawBitmap(bitmap, source, destination, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
        canvas.drawRoundRect(
            destination,
            28f,
            28f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#35507A")
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
        )
    }

    /**
     * Desenha o recorte real da placa no canto inferior esquerdo, com a mesma
     * borda azul usada na tela de detalhes. Nenhum caractere é redesenhado.
     */
    private fun drawPlateCrop(canvas: Canvas, bitmap: Bitmap, photoRect: RectF) {
        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
        if (ratio <= 0f) return
        var cropWidth = photoRect.width() * 0.38f
        var cropHeight = cropWidth / ratio
        val maxHeight = photoRect.height() * 0.32f
        if (cropHeight > maxHeight) {
            cropHeight = maxHeight
            cropWidth = cropHeight * ratio
        }
        val margin = 20f
        val destination = RectF(
            photoRect.left + margin,
            photoRect.bottom - margin - cropHeight,
            photoRect.left + margin + cropWidth,
            photoRect.bottom - margin
        )
        val background = RectF(
            destination.left - 4f,
            destination.top - 4f,
            destination.right + 4f,
            destination.bottom + 4f
        )
        canvas.drawRoundRect(
            background,
            10f,
            10f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(170, 0, 0, 0)
                style = Paint.Style.FILL
            }
        )
        val path = Path().apply {
            addRoundRect(destination, 8f, 8f, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(bitmap, null, destination, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
        canvas.drawRoundRect(
            destination,
            8f,
            8f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentColor
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
        )
    }

    private fun centerCropRect(bitmap: Bitmap, destination: RectF): Rect {
        val targetRatio = destination.width() / destination.height()
        val sourceRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        return if (sourceRatio > targetRatio) {
            val cropWidth = max((bitmap.height * targetRatio).toInt(), 1)
            val left = (bitmap.width - cropWidth) / 2
            Rect(left, 0, left + cropWidth, bitmap.height)
        } else {
            val cropHeight = max((bitmap.width / targetRatio).toInt(), 1)
            val top = (bitmap.height - cropHeight) / 2
            Rect(0, top, bitmap.width, top + cropHeight)
        }
    }

    private fun decodePhoto(file: File): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 1600) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(file.absolutePath, options)
    }.getOrNull()

    private fun cleanup(directory: File) {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        val files = directory.listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()
        files.forEachIndexed { index, file ->
            if (index >= 20 || file.lastModified() < cutoff) {
                runCatching { file.delete() }
            }
        }
    }

    private data class CardRow(val label: String, val value: String)
}
