package com.neoutils.finsight.report

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.neoutils.finsight.domain.model.ReportDocument
import com.neoutils.finsight.extension.PlatformContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class JvmReportShareService : ReportShareService {

    override suspend fun share(document: ReportDocument, context: PlatformContext): Either<ReportOutputError, Unit> {
        if (document.format != ReportDocument.Format.HTML) {
            return ReportOutputError.UNSUPPORTED_FORMAT.left()
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

                val result = chooser.showSaveDialog(context.windowScope.window)

                if (result == JFileChooser.APPROVE_OPTION) {
                    Either.catch { chooser.selectedFile.writeBytes(document.content) }
                        .fold(
                            ifLeft = { continuation.resume(ReportOutputError.IO_ERROR.left()) },
                            ifRight = { continuation.resume(Unit.right()) },
                        )
                } else {
                    continuation.resume(Unit.right())
                }
            }
        }
    }
}
