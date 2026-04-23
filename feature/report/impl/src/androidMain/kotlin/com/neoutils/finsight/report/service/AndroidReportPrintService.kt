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
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.domain.error.ReportOutputError
import com.neoutils.finsight.ui.screen.report.service.ReportPrintService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidReportPrintService : ReportPrintService {

    override suspend fun print(
        document: ReportDocument,
        context: PlatformContext
    ): Either<ReportOutputError, Unit> {
        if (document.format != ReportDocument.Format.HTML) {
            return ReportOutputError.UNSUPPORTED_FORMAT.left()
        }

        return suspendCancellableCoroutine { continuation ->
            context.activity.runOnUiThread {
                Either.catch {
                    val webView = WebView(context.activity)
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            val printManager = context.activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
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
