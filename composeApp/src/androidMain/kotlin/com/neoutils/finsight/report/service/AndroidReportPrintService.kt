package com.neoutils.finsight.report.service

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.neoutils.finsight.domain.model.ReportDocument
import com.neoutils.finsight.report.ActivityHolder
import com.neoutils.finsight.report.ReportOutputError
import com.neoutils.finsight.report.ReportPrintService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidReportPrintService(
    private val activityHolder: ActivityHolder,
) : ReportPrintService {

    override suspend fun print(document: ReportDocument): Either<ReportOutputError, Unit> {
        if (document.format != ReportDocument.Format.HTML) {
            return ReportOutputError.UNSUPPORTED_FORMAT.left()
        }

        val activity = activityHolder.activity
            ?: return ReportOutputError.UNKNOWN.left()

        return suspendCancellableCoroutine { continuation ->
            activity.runOnUiThread {
                Either.catch {
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
                            continuation.resume(Unit.right())
                        }
                    }
                    webView.loadDataWithBaseURL(
                        null,
                        document.content.decodeToString(),
                        document.format.mimeType,
                        Charsets.UTF_8.name(),
                        null,
                    )
                }.onLeft {
                    continuation.resume(ReportOutputError.UNKNOWN.left())
                }
            }
        }
    }
}
