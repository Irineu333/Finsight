package com.neoutils.finsight.report.service

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.neoutils.finsight.domain.model.ReportDocument
import com.neoutils.finsight.report.ActivityHolder
import com.neoutils.finsight.report.ReportOutputError
import com.neoutils.finsight.report.ReportOutputResult
import com.neoutils.finsight.report.ReportPrintService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidReportPrintService(
    private val activityHolder: ActivityHolder,
) : ReportPrintService {

    override suspend fun print(document: ReportDocument): ReportOutputResult {
        if (document.format != ReportDocument.Format.HTML) {
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
