package com.neoutils.finsight.report

import com.neoutils.finsight.domain.model.ReportDocument
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class JvmReportShareService : ReportShareService {

    override suspend fun share(document: ReportDocument): ReportOutputResult {
        if (document.format != ReportDocument.Format.HTML) {
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
}
