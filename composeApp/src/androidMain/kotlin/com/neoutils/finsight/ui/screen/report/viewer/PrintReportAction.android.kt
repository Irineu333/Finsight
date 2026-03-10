package com.neoutils.finsight.ui.screen.report.viewer

import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberPrintReportAction(html: String, title: String): () -> Unit {
    val context = LocalContext.current
    return remember(html, title) {
        {
            val webView = WebView(context)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    val printManager = context.getSystemService(PrintManager::class.java)
                    val adapter = view.createPrintDocumentAdapter(title)
                    printManager.print(title, adapter, PrintAttributes.Builder().build())
                }
            }
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }
    }
}
