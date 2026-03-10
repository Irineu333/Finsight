package com.neoutils.finsight.ui.screen.report.viewer

import androidx.compose.runtime.Composable

@Composable
internal expect fun rememberPrintReportAction(html: String, title: String): () -> Unit
