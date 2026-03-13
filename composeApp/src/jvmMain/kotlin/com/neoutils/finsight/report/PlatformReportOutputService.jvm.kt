package com.neoutils.finsight.report

import java.awt.Desktop
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class JvmReportOutputService : ReportOutputService {

    override suspend fun share(document: ReportDocument): ReportOutputResult {
        if (document.format != ReportDocumentFormat.HTML) {
            return ReportOutputResult.Failure(ReportOutputError.UnsupportedFormat)
        }

        return suspendCancellableCoroutine { continuation ->
            SwingUtilities.invokeLater {
                val chooser = JFileChooser().apply {
                    dialogTitle = document.fileNameWithoutExtension
                    selectedFile = File(document.fileName)
                    fileFilter = FileNameExtensionFilter(
                        "HTML (*.${document.format.fileExtension})",
                        document.format.fileExtension,
                    )
                }

                val result = chooser.showSaveDialog(null)

                if (result == JFileChooser.APPROVE_OPTION) {
                    runCatching {
                        chooser.selectedFile.writeBytes(document.content)
                        continuation.resume(ReportOutputResult.Success(chooser.selectedFile.absolutePath))
                    }.onFailure {
                        continuation.resume(ReportOutputResult.Failure(ReportOutputError.IoError))
                    }
                } else {
                    continuation.resume(ReportOutputResult.Success())
                }
            }
        }
    }

    override suspend fun print(document: ReportDocument): ReportOutputResult = withContext(Dispatchers.IO) {
        if (document.format != ReportDocumentFormat.HTML) {
            return@withContext ReportOutputResult.Failure(ReportOutputError.UnsupportedFormat)
        }

        runCatching {
            val reportFile = File.createTempFile("finsight-report-", ".${document.format.fileExtension}")
            reportFile.writeBytes(document.content)
            reportFile.deleteOnExit()

            if (Desktop.isDesktopSupported()) {
                val desktop = Desktop.getDesktop()
                when {
                    desktop.isSupported(Desktop.Action.PRINT) -> desktop.print(reportFile)
                    desktop.isSupported(Desktop.Action.BROWSE) -> desktop.browse(reportFile.toURI())
                }
            }

            ReportOutputResult.Success()
        }.getOrElse { ReportOutputResult.Failure(ReportOutputError.IoError) }
    }
}
