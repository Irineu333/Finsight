package com.neoutils.finsight.ui.screen.reports

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class AndroidReportExportService(
    private val context: Context,
) : ReportExportService {

    override suspend fun exportAndShare(preview: GeneratedReportPreview): ReportExportResult {
        return try {
            val file = withContext(Dispatchers.IO) {
                val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
                File(reportsDir, sanitizeFileName(preview.suggestedFileName)).apply {
                    when (preview.requestedFormat) {
                        ReportFormat.CSV -> writeText(preview.content)
                        ReportFormat.PDF -> writePdf(preview = preview, file = this)
                    }
                }
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )

            withContext(Dispatchers.Main) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = fileMimeType(preview.requestedFormat)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, preview.title)
                    putExtra(Intent.EXTRA_TEXT, preview.title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooserIntent = Intent.createChooser(shareIntent, preview.title).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                context.startActivity(chooserIntent)
            }

            ReportExportResult.Success(file.name)
        } catch (error: Throwable) {
            ReportExportResult.Failure(
                reason = error.message ?: "Nao foi possivel compartilhar o arquivo.",
            )
        }
    }

    private fun writePdf(
        preview: GeneratedReportPreview,
        file: File,
    ) {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40
        val lineHeight = 16
        val titlePaint = Paint().apply {
            textSize = 20f
            isFakeBoldText = true
            color = Color.BLACK
        }
        val subtitlePaint = Paint().apply {
            textSize = 11f
            color = Color.DKGRAY
        }
        val sectionTitlePaint = Paint().apply {
            textSize = 14f
            isFakeBoldText = true
            color = Color.BLACK
        }
        val labelPaint = Paint().apply {
            textSize = 10f
            color = Color.DKGRAY
        }
        val valuePaint = Paint().apply {
            textSize = 13f
            isFakeBoldText = true
            color = Color.BLACK
        }
        val bodyPaint = Paint().apply {
            textSize = 11f
            typeface = Typeface.MONOSPACE
            color = Color.BLACK
        }
        val chipPaint = Paint().apply {
            color = Color.rgb(236, 242, 255)
            style = Paint.Style.FILL
        }
        val sectionPaint = Paint().apply {
            color = Color.rgb(247, 247, 248)
            style = Paint.Style.FILL
        }
        val dividerPaint = Paint().apply {
            color = Color.rgb(224, 224, 224)
            strokeWidth = 1f
        }

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = margin.toFloat()

        fun newPage() {
            document.finishPage(page)
            pageNumber += 1
            page = document.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            )
            canvas = page.canvas
            y = margin.toFloat()
            drawHeader(canvas, preview, margin, pageWidth, y, titlePaint, subtitlePaint)
            y += 54f
        }

        drawHeader(canvas, preview, margin, pageWidth, y, titlePaint, subtitlePaint)
        y += 54f

        preview.highlights.chunked(2).forEach { row ->
            if (y > pageHeight - 120) newPage()
            val cardWidth = (pageWidth - margin * 2 - 12) / 2f
            row.forEachIndexed { index, item ->
                val left = margin + (cardWidth + 12f) * index
                val top = y
                val rect = RectF(left, top, left + cardWidth, top + 58f)
                canvas.drawRoundRect(rect, 14f, 14f, chipPaint)
                canvas.drawText(item.label, left + 14f, top + 20f, labelPaint)
                wrapLine(item.value, valuePaint, (cardWidth - 28f).toInt())
                    .take(2)
                    .forEachIndexed { lineIndex, line ->
                        canvas.drawText(line, left + 14f, top + 40f + 14f * lineIndex, valuePaint)
                    }
            }
            y += 74f
        }

        preview.sections.forEach { section ->
            if (y > pageHeight - 120) newPage()
            canvas.drawRoundRect(
                RectF(margin.toFloat(), y, (pageWidth - margin).toFloat(), y + 26f),
                12f,
                12f,
                sectionPaint,
            )
            canvas.drawText(section.title, (margin + 14).toFloat(), y + 17f, sectionTitlePaint)
            y += 38f

            section.body.forEach { line ->
                if (y > pageHeight - 80) newPage()
                wrapLine(line, bodyPaint, pageWidth - margin * 2).forEach { wrapped ->
                    canvas.drawText(wrapped, margin.toFloat(), y, bodyPaint)
                    y += lineHeight.toFloat()
                }
                y += 4f
            }

            if (section.columns.isNotEmpty() && section.rows.isNotEmpty()) {
                if (y > pageHeight - 100) newPage()
                val columnCount = section.columns.size
                val columnWidth = (pageWidth - margin * 2).toFloat() / columnCount

                section.columns.forEachIndexed { index, column ->
                    canvas.drawText(
                        column,
                        margin + columnWidth * index + 4f,
                        y,
                        labelPaint
                    )
                }
                y += 10f
                canvas.drawLine(
                    margin.toFloat(),
                    y,
                    (pageWidth - margin).toFloat(),
                    y,
                    dividerPaint
                )
                y += 14f

                section.rows.forEach { row ->
                    if (y > pageHeight - 70) newPage()
                    row.forEachIndexed { index, cell ->
                        wrapLine(cell, bodyPaint, (columnWidth - 8f).toInt())
                            .take(2)
                            .forEachIndexed { lineIndex, line ->
                                canvas.drawText(
                                    line,
                                    margin + columnWidth * index + 4f,
                                    y + lineHeight * lineIndex,
                                    bodyPaint,
                                )
                            }
                    }
                    y += lineHeight * 2f + 6f
                    canvas.drawLine(
                        margin.toFloat(),
                        y - 4f,
                        (pageWidth - margin).toFloat(),
                        y - 4f,
                        dividerPaint
                    )
                }
            }

            y += 12f
        }

        document.finishPage(page)
        FileOutputStream(file).use { output ->
            document.writeTo(output)
        }
        document.close()
    }

    private fun drawHeader(
        canvas: android.graphics.Canvas,
        preview: GeneratedReportPreview,
        margin: Int,
        pageWidth: Int,
        top: Float,
        titlePaint: Paint,
        subtitlePaint: Paint,
    ) {
        val headerPaint = Paint().apply {
            color = Color.rgb(242, 246, 252)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            RectF(margin.toFloat(), top, (pageWidth - margin).toFloat(), top + 42f),
            18f,
            18f,
            headerPaint,
        )
        canvas.drawText(preview.title, (margin + 16).toFloat(), top + 18f, titlePaint)
        canvas.drawText(preview.subtitle, (margin + 16).toFloat(), top + 34f, subtitlePaint)
    }

    private fun wrapLine(
        line: String,
        paint: Paint,
        maxWidth: Int,
    ): List<String> {
        if (line.isEmpty()) return listOf("")

        val wrapped = mutableListOf<String>()
        var remaining = line

        while (remaining.isNotEmpty()) {
            var cutIndex = remaining.length
            while (cutIndex > 0 && paint.measureText(remaining.substring(0, cutIndex)) > maxWidth) {
                cutIndex--
            }

            if (cutIndex <= 0) {
                cutIndex = 1
            } else if (cutIndex < remaining.length) {
                val lastSpace = remaining.substring(0, cutIndex).lastIndexOf(' ')
                if (lastSpace > 0) {
                    cutIndex = lastSpace
                }
            }

            wrapped += remaining.substring(0, cutIndex).trimEnd()
            remaining = remaining.substring(cutIndex).trimStart()
        }

        return wrapped
    }

    private fun fileMimeType(format: ReportFormat): String {
        return when (format) {
            ReportFormat.CSV -> "text/csv"
            ReportFormat.PDF -> "application/pdf"
        }
    }

    private fun sanitizeFileName(fileName: String): String {
        return fileName.replace(Regex("[^A-Za-z0-9._-]"), "-")
    }
}
