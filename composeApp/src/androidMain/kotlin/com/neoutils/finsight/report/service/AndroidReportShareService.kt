package com.neoutils.finsight.report.service

import android.content.Intent
import androidx.core.content.FileProvider
import arrow.core.Either
import arrow.core.left
import com.neoutils.finsight.domain.model.ReportDocument
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.report.ReportOutputError
import com.neoutils.finsight.report.ReportShareService
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidReportShareService : ReportShareService {

    override suspend fun share(
        document: ReportDocument,
        context: PlatformContext
    ): Either<ReportOutputError, Unit> = withContext(Dispatchers.IO) {
        if (document.format != ReportDocument.Format.HTML) {
            return@withContext ReportOutputError.UNSUPPORTED_FORMAT.left()
        }

        Either.catch {
            val activity = context.activity
            val reportsDir = File(activity.cacheDir, "reports").apply { mkdirs() }
            val reportFile = File(reportsDir, document.fileName)
            reportFile.writeBytes(document.content)
            val contentUri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                reportFile,
            )
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = document.format.mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooserIntent = Intent.createChooser(sendIntent, null).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(chooserIntent)
        }.mapLeft { ReportOutputError.IO_ERROR }
    }
}
