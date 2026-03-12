package com.neoutils.finsight.report

import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class AndroidReportOutputService(
    private val context: Context,
    private val activityHolder: ActivityHolder,
) : ReportOutputService {

    override suspend fun export(document: ReportDocument): ReportOutputResult = withContext(Dispatchers.IO) {
        if (document.format != ReportDocumentFormat.HTML) {
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

    override suspend fun print(document: ReportDocument): ReportOutputResult {
        if (document.format != ReportDocumentFormat.HTML) {
            return ReportOutputResult.Failure(ReportOutputError.UnsupportedFormat)
        }

        val activity = activityHolder.activity
            ?: return ReportOutputResult.Failure(ReportOutputError.Unknown)

        return suspendCancellableCoroutine { continuation ->
            activity.runOnUiThread {
                runCatching {
                    val webView = WebView(activity)
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            val printManager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
                            val adapter = view.createPrintDocumentAdapter(document.fileNameWithoutExtension)
                            printManager.print(
                                document.fileNameWithoutExtension,
                                adapter,
                                PrintAttributes.Builder().build(),
                            )
                            continuation.resume(ReportOutputResult.Success())
                        }
                    }
                    webView.loadDataWithBaseURL(
                        null,
                        document.content.decodeToString(),
                        document.format.mimeType,
                        Charsets.UTF_8.name(),
                        null,
                    )
                }.onFailure {
                    continuation.resume(ReportOutputResult.Failure(ReportOutputError.Unknown))
                }
            }
        }
    }
}
