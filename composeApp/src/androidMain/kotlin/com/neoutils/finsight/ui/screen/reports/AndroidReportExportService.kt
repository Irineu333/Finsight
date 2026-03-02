package com.neoutils.finsight.ui.screen.reports

import android.content.Context
import android.content.Intent
import android.graphics.Paint
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
        val lineHeight = 18
        val titlePaint = Paint().apply {
            textSize = 18f
            isFakeBoldText = true
        }
        val bodyPaint = Paint().apply {
            textSize = 11f
            typeface = Typeface.MONOSPACE
        }

        val lines = preview.content.lines()
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = margin.toFloat()

        canvas.drawText(preview.title, margin.toFloat(), y, titlePaint)
        y += 28f

        lines.forEach { line ->
            if (y > pageHeight - margin) {
                document.finishPage(page)
                pageNumber += 1
                page = document.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                )
                canvas = page.canvas
                y = margin.toFloat()
                canvas.drawText(preview.title, margin.toFloat(), y, titlePaint)
                y += 28f
            }

            wrapLine(line, bodyPaint, pageWidth - margin * 2).forEach { wrappedLine ->
                if (y > pageHeight - margin) {
                    document.finishPage(page)
                    pageNumber += 1
                    page = document.startPage(
                        PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    )
                    canvas = page.canvas
                    y = margin.toFloat()
                    canvas.drawText(preview.title, margin.toFloat(), y, titlePaint)
                    y += 28f
                }

                canvas.drawText(wrappedLine, margin.toFloat(), y, bodyPaint)
                y += lineHeight.toFloat()
            }
        }

        document.finishPage(page)
        FileOutputStream(file).use { output ->
            document.writeTo(output)
        }
        document.close()
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
