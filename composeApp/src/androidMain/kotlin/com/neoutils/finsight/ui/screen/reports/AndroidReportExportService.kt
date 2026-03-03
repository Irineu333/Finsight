package com.neoutils.finsight.ui.screen.reports

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AndroidReportExportService(
    private val context: Context,
) : ReportExportService {

    override suspend fun exportAndShare(document: ReportDocument): ReportExportResult {
        return try {
            val file = withContext(Dispatchers.IO) {
                val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
                File(reportsDir, sanitizeFileName(document.fileName)).apply {
                    when (document) {
                        is ReportDocument.Csv -> writeText(document.content)
                        is ReportDocument.Pdf -> writePdf(report = document, file = this)
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
                    type = document.mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, document.title)
                    putExtra(Intent.EXTRA_TEXT, document.title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooserIntent = Intent.createChooser(shareIntent, document.title).apply {
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
        report: ReportDocument.Pdf,
        file: File,
    ) {
        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 36
        val contentWidth = pageWidth - margin * 2
        val footerHeight = 24f
        val bottomLimit = pageHeight - margin - footerHeight
        val generatedAt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

        val headerSurfacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val headerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(210, 214, 219)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val titlePaint = Paint().apply {
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(19, 33, 48)
        }
        val subtitlePaint = Paint().apply {
            textSize = 11f
            color = Color.rgb(83, 97, 112)
        }
        val metaPaint = Paint().apply {
            textSize = 10f
            color = Color.rgb(95, 101, 108)
        }
        val formatPaint = Paint().apply {
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            color = Color.rgb(72, 76, 82)
        }
        val formatChipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(244, 245, 246)
            style = Paint.Style.FILL
        }
        val sectionTitlePaint = Paint().apply {
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(19, 33, 48)
        }
        val labelPaint = Paint().apply {
            textSize = 10f
            color = Color.rgb(87, 100, 115)
        }
        val valuePaint = Paint().apply {
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(19, 33, 48)
        }
        val bodyPaint = Paint().apply {
            textSize = 11f
            color = Color.rgb(36, 47, 58)
        }
        val bodyStrongPaint = Paint(bodyPaint).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val sectionSurfacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(250, 250, 250)
            style = Paint.Style.FILL
        }
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(220, 224, 228)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val sectionAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(160, 165, 171)
            style = Paint.Style.FILL
        }
        val tableHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(242, 243, 244)
            style = Paint.Style.FILL
        }
        val tableRowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val tableAltRowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(248, 248, 248)
            style = Paint.Style.FILL
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(221, 228, 235)
            strokeWidth = 1f
        }
        val footerPaint = Paint().apply {
            textSize = 9f
            color = Color.rgb(110, 122, 135)
        }

        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = margin.toFloat()

        fun newPage() {
            drawFooter(
                canvas = canvas,
                pageWidth = pageWidth,
                pageHeight = pageHeight,
                margin = margin,
                pageNumber = pageNumber,
                generatedAt = generatedAt,
                footerPaint = footerPaint,
                dividerPaint = dividerPaint,
            )
            pdfDocument.finishPage(page)
            pageNumber += 1
            page = pdfDocument.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            )
            canvas = page.canvas
            y = margin.toFloat()
            y = drawHeader(
                canvas = canvas,
                document = report,
                margin = margin,
                pageWidth = pageWidth,
                top = y,
                generatedAt = generatedAt,
                headerSurfacePaint = headerSurfacePaint,
                headerBorderPaint = headerBorderPaint,
                titlePaint = titlePaint,
                subtitlePaint = subtitlePaint,
                metaPaint = metaPaint,
                formatPaint = formatPaint,
                formatChipPaint = formatChipPaint,
            ) + 18f
        }

        y = drawHeader(
            canvas = canvas,
            document = report,
            margin = margin,
            pageWidth = pageWidth,
            top = y,
            generatedAt = generatedAt,
            headerSurfacePaint = headerSurfacePaint,
            headerBorderPaint = headerBorderPaint,
            titlePaint = titlePaint,
            subtitlePaint = subtitlePaint,
            metaPaint = metaPaint,
            formatPaint = formatPaint,
            formatChipPaint = formatChipPaint,
        ) + 18f

        report.highlights.chunked(2).forEach { row ->
            val cardWidth = (contentWidth - 12) / 2f
            val cardHeight = row.maxOf { item ->
                val wrappedValue = wrapLine(item.value, valuePaint, (cardWidth - 28f).toInt()).take(2)
                50f + (wrappedValue.size - 1).coerceAtLeast(0) * 14f
            }
            if (y + cardHeight > bottomLimit) newPage()
            row.forEachIndexed { index, item ->
                val left = margin + (cardWidth + 12f) * index
                val top = y
                val rect = RectF(left, top, left + cardWidth, top + cardHeight)
                canvas.drawRoundRect(rect, 16f, 16f, highlightPaint)
                canvas.drawRoundRect(rect, 16f, 16f, cardBorderPaint)
                canvas.drawText(item.label.uppercase(Locale.getDefault()), left + 18f, top + 24f, labelPaint)
                wrapLine(item.value, valuePaint, (cardWidth - 40f).toInt())
                    .take(2)
                    .forEachIndexed { lineIndex, line ->
                        canvas.drawText(line, left + 18f, top + 46f + 14f * lineIndex, valuePaint)
                    }
            }
            y += cardHeight + 12f
        }

        report.sections.forEach { section ->
            if (y + 54f > bottomLimit) newPage()
            y = drawSectionHeader(
                canvas = canvas,
                title = section.title,
                margin = margin,
                contentWidth = contentWidth.toFloat(),
                top = y,
                sectionAccentPaint = sectionAccentPaint,
                sectionTitlePaint = sectionTitlePaint,
            )

            if (section.body.isNotEmpty()) {
                val bodyLines = section.body.flatMap { line ->
                    wrapStyledLine(
                        segments = lineToSegments(line),
                        regularPaint = bodyPaint,
                        strongPaint = bodyStrongPaint,
                        maxWidth = contentWidth,
                    )
                }
                val bodyHeight = 20f + bodyLines.size * 16f
                if (y + bodyHeight > bottomLimit) {
                    newPage()
                    y = drawSectionHeader(
                        canvas = canvas,
                        title = section.title,
                        margin = margin,
                        contentWidth = contentWidth.toFloat(),
                        top = y,
                        sectionAccentPaint = sectionAccentPaint,
                        sectionTitlePaint = sectionTitlePaint,
                    )
                }
                canvas.drawRoundRect(
                    RectF(margin.toFloat(), y, (margin + contentWidth).toFloat(), y + bodyHeight),
                    14f,
                    14f,
                    sectionSurfacePaint,
                )
                canvas.drawRoundRect(
                    RectF(margin.toFloat(), y, (margin + contentWidth).toFloat(), y + bodyHeight),
                    14f,
                    14f,
                    cardBorderPaint,
                )
                var bodyY = y + 20f
                bodyLines.forEach { segments ->
                    drawStyledLine(
                        canvas = canvas,
                        segments = segments,
                        x = (margin + 16).toFloat(),
                        baseline = bodyY,
                    )
                    bodyY += 16f
                }
                y += bodyHeight + 12f
            }

            section.table?.let { table ->
                val tableLayout = resolveTableLayout(table.columns, contentWidth.toFloat())
                y = drawTable(
                    canvas = canvas,
                    table = table,
                    tableLayout = tableLayout,
                    margin = margin,
                    top = y,
                    bottomLimit = bottomLimit,
                    pageWidth = pageWidth,
                    onNewPage = {
                        newPage()
                        drawSectionHeader(
                            canvas = canvas,
                            title = section.title,
                            margin = margin,
                            contentWidth = contentWidth.toFloat(),
                            top = y,
                            sectionAccentPaint = sectionAccentPaint,
                            sectionTitlePaint = sectionTitlePaint,
                        )
                    },
                    tableHeaderPaint = tableHeaderPaint,
                    tableRowPaint = tableRowPaint,
                    tableAltRowPaint = tableAltRowPaint,
                    cardBorderPaint = cardBorderPaint,
                    labelPaint = labelPaint,
                    bodyPaint = bodyPaint,
                    dividerPaint = dividerPaint,
                )
            }

            y += 6f
        }

        drawFooter(
            canvas = canvas,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            margin = margin,
            pageNumber = pageNumber,
            generatedAt = generatedAt,
            footerPaint = footerPaint,
            dividerPaint = dividerPaint,
        )
        pdfDocument.finishPage(page)
        FileOutputStream(file).use { output ->
            pdfDocument.writeTo(output)
        }
        pdfDocument.close()
    }

    private fun drawHeader(
        canvas: Canvas,
        document: ReportDocument.Pdf,
        margin: Int,
        pageWidth: Int,
        top: Float,
        generatedAt: String,
        headerSurfacePaint: Paint,
        headerBorderPaint: Paint,
        titlePaint: Paint,
        subtitlePaint: Paint,
        metaPaint: Paint,
        formatPaint: Paint,
        formatChipPaint: Paint,
    ): Float {
        val headerHeight = 86f
        canvas.drawRoundRect(
            RectF(margin.toFloat(), top, (pageWidth - margin).toFloat(), top + headerHeight),
            18f,
            18f,
            headerSurfacePaint,
        )
        canvas.drawRoundRect(
            RectF(margin.toFloat(), top, (pageWidth - margin).toFloat(), top + headerHeight),
            18f,
            18f,
            headerBorderPaint,
        )

        val left = margin + 18f
        canvas.drawText(document.title, left, top + 26f, titlePaint)
        canvas.drawText(document.subtitle, left, top + 44f, subtitlePaint)
        canvas.drawText("Arquivo: ${document.fileName}", left, top + 64f, metaPaint)
        canvas.drawText("Gerado em $generatedAt", left, top + 78f, metaPaint)

        val chipWidth = 58f
        val chipLeft = pageWidth - margin - chipWidth
        val chipTop = top + 18f
        canvas.drawRoundRect(
            RectF(chipLeft, chipTop, chipLeft + chipWidth, chipTop + 22f),
            11f,
            11f,
            formatChipPaint,
        )
        canvas.drawText(
            document.format.name,
            chipLeft + chipWidth / 2f,
            chipTop + 15f,
            formatPaint,
        )

        return top + headerHeight
    }

    private fun drawSectionHeader(
        canvas: Canvas,
        title: String,
        margin: Int,
        contentWidth: Float,
        top: Float,
        sectionAccentPaint: Paint,
        sectionTitlePaint: Paint,
    ): Float {
        canvas.drawText(title, margin.toFloat(), top + 16f, sectionTitlePaint)
        canvas.drawLine(
            margin.toFloat(),
            top + 28f,
            margin + contentWidth,
            top + 28f,
            sectionAccentPaint,
        )
        return top + 40f
    }

    private fun drawFooter(
        canvas: Canvas,
        pageWidth: Int,
        pageHeight: Int,
        margin: Int,
        pageNumber: Int,
        generatedAt: String,
        footerPaint: Paint,
        dividerPaint: Paint,
    ) {
        val y = (pageHeight - margin).toFloat()
        canvas.drawLine(margin.toFloat(), y - 14f, (pageWidth - margin).toFloat(), y - 14f, dividerPaint)
        canvas.drawText("Gerado em $generatedAt", margin.toFloat(), y, footerPaint)
        footerPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Pagina $pageNumber", (pageWidth - margin).toFloat(), y, footerPaint)
        footerPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawTable(
        canvas: Canvas,
        table: ReportTable,
        tableLayout: PdfTableLayout,
        margin: Int,
        top: Float,
        bottomLimit: Float,
        pageWidth: Int,
        onNewPage: () -> Float,
        tableHeaderPaint: Paint,
        tableRowPaint: Paint,
        tableAltRowPaint: Paint,
        cardBorderPaint: Paint,
        labelPaint: Paint,
        bodyPaint: Paint,
        dividerPaint: Paint,
    ): Float {
        var y = top

        fun drawHeaderRow() {
            canvas.drawRoundRect(
                RectF(margin.toFloat(), y, (pageWidth - margin).toFloat(), y + 28f),
                12f,
                12f,
                tableHeaderPaint,
            )
            var currentX = margin.toFloat()
            table.columns.forEachIndexed { index, column ->
                val cellWidth = tableLayout.columnWidths[index]
                drawCellText(
                    canvas = canvas,
                    text = column.uppercase(Locale.getDefault()),
                    x = currentX + 8f,
                    width = cellWidth - 16f,
                    baseline = y + 18f,
                    paint = labelPaint,
                    alignRight = tableLayout.rightAlignedColumns.contains(index),
                    maxLines = 1,
                )
                currentX += cellWidth
            }
            y += 36f
        }

        drawHeaderRow()

        table.rows.forEachIndexed { rowIndex, row ->
            val wrappedCells = row.mapIndexed { index, cell ->
                wrapLine(
                    cell,
                    bodyPaint,
                    (tableLayout.columnWidths[index] - 16f).toInt(),
                ).take(3)
            }
            val rowHeight = 16f + wrappedCells.maxOf { lines -> lines.size } * 14f

            if (y + rowHeight > bottomLimit) {
                y = onNewPage()
                drawHeaderRow()
            }

            canvas.drawRoundRect(
                RectF(margin.toFloat(), y, (pageWidth - margin).toFloat(), y + rowHeight),
                10f,
                10f,
                if (rowIndex % 2 == 0) tableRowPaint else tableAltRowPaint,
            )
            canvas.drawRoundRect(
                RectF(margin.toFloat(), y, (pageWidth - margin).toFloat(), y + rowHeight),
                10f,
                10f,
                cardBorderPaint,
            )

            var currentX = margin.toFloat()
            wrappedCells.forEachIndexed { index, lines ->
                lines.forEachIndexed { lineIndex, line ->
                    drawCellText(
                        canvas = canvas,
                        text = line,
                        x = currentX + 8f,
                        width = tableLayout.columnWidths[index] - 16f,
                        baseline = y + 18f + lineIndex * 14f,
                        paint = bodyPaint,
                        alignRight = tableLayout.rightAlignedColumns.contains(index),
                        maxLines = 1,
                    )
                }
                currentX += tableLayout.columnWidths[index]
            }

            canvas.drawLine(
                margin.toFloat(),
                y + rowHeight + 4f,
                (pageWidth - margin).toFloat(),
                y + rowHeight + 4f,
                dividerPaint,
            )
            y += rowHeight + 8f
        }

        return y
    }

    private fun drawCellText(
        canvas: Canvas,
        text: String,
        x: Float,
        width: Float,
        baseline: Float,
        paint: Paint,
        alignRight: Boolean,
        maxLines: Int,
    ) {
        val lines = wrapLine(text, paint, width.toInt()).take(maxLines)
        val originalAlign = paint.textAlign
        if (alignRight) {
            paint.textAlign = Paint.Align.RIGHT
            lines.forEachIndexed { index, line ->
                canvas.drawText(line, x + width, baseline + index * 14f, paint)
            }
        } else {
            paint.textAlign = Paint.Align.LEFT
            lines.forEachIndexed { index, line ->
                canvas.drawText(line, x, baseline + index * 14f, paint)
            }
        }
        paint.textAlign = originalAlign
    }

    private fun resolveTableLayout(
        columns: List<String>,
        contentWidth: Float,
    ): PdfTableLayout {
        val normalized = columns.map { it.lowercase(Locale.getDefault()) }
        val weights = when {
            normalized == listOf("data", "tipo", "descricao", "impacto") ||
                normalized == listOf("data", "tipo", "descricao", "valor") ->
                listOf(0.16f, 0.24f, 0.38f, 0.22f)

            normalized == listOf("data", "tipo", "descricao", "conta/cartao", "valor") ->
                listOf(0.14f, 0.18f, 0.34f, 0.18f, 0.16f)

            else -> List(columns.size) { 1f / columns.size }
        }
        val rightAligned = normalized.mapIndexedNotNull { index, name ->
            if (name.contains("valor") || name.contains("impacto") || name.contains("total")) index else null
        }.toSet()

        return PdfTableLayout(
            columnWidths = weights.map { it * contentWidth },
            rightAlignedColumns = rightAligned,
        )
    }

    private fun lineToSegments(
        line: String,
    ): List<StyledSegment> {
        val separator = line.indexOf(':')
        if (separator <= 0) {
            return listOf(StyledSegment(text = line, strong = false))
        }

        return listOf(
            StyledSegment(text = line.substring(0, separator + 1), strong = true),
            StyledSegment(text = " ${line.substring(separator + 1).trimStart()}", strong = false),
        )
    }

    private fun wrapStyledLine(
        segments: List<StyledSegment>,
        regularPaint: Paint,
        strongPaint: Paint,
        maxWidth: Int,
    ): List<List<StyledSegment>> {
        val lines = mutableListOf<MutableList<StyledSegment>>()
        var currentLine = mutableListOf<StyledSegment>()
        var currentWidth = 0f

        segments.forEach { part ->
            val paint = if (part.strong) strongPaint else regularPaint
            val words = part.text.split(" ")
            words.forEachIndexed { index, word ->
                val token = when {
                    index == words.lastIndex -> word
                    else -> "$word "
                }
                val tokenWidth = paint.measureText(token)
                if (currentLine.isNotEmpty() && currentWidth + tokenWidth > maxWidth) {
                    lines += currentLine
                    currentLine = mutableListOf()
                    currentWidth = 0f
                }
                currentLine += StyledSegment(token, part.strong)
                currentWidth += tokenWidth
            }
        }

        if (currentLine.isNotEmpty()) {
            lines += currentLine
        }

        return lines
    }

    private fun drawStyledLine(
        canvas: Canvas,
        segments: List<StyledSegment>,
        x: Float,
        baseline: Float,
    ) {
        var currentX = x
        segments.forEach { segment ->
            val paint = if (segment.strong) {
                Paint().apply {
                    color = Color.rgb(36, 47, 58)
                    textSize = 11f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
            } else {
                Paint().apply {
                    color = Color.rgb(36, 47, 58)
                    textSize = 11f
                }
            }
            canvas.drawText(segment.text, currentX, baseline, paint)
            currentX += paint.measureText(segment.text)
        }
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

    private fun sanitizeFileName(fileName: String): String {
        return fileName.replace(Regex("[^A-Za-z0-9._-]"), "-")
    }

    private data class PdfTableLayout(
        val columnWidths: List<Float>,
        val rightAlignedColumns: Set<Int>,
    )

    private data class StyledSegment(
        val text: String,
        val strong: Boolean,
    )
}
