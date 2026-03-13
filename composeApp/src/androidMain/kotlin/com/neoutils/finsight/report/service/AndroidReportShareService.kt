package com.neoutils.finsight.report.service

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.neoutils.finsight.domain.model.ReportDocument
import com.neoutils.finsight.report.ReportOutputError
import com.neoutils.finsight.report.ReportOutputResult
import com.neoutils.finsight.report.ReportShareService
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidReportShareService(
    private val context: Context,
) : ReportShareService {

    override suspend fun share(document: ReportDocument): ReportOutputResult = withContext(Dispatchers.IO) {
        if (document.format != ReportDocument.Format.HTML) {
            return@withContext ReportOutputResult.Failure(ReportOutputError.UnsupportedFormat)
        }

        runCatching {
            val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val reportFile = File(reportsDir, document.fileName)
            reportFile.writeBytes(document.content)
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
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
            context.startActivity(chooserIntent)
            ReportOutputResult.Success()
        }.getOrElse {
            ReportOutputResult.Failure(ReportOutputError.IoError)
        }
    }
}
