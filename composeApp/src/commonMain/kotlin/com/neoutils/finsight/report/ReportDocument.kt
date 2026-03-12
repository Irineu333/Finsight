package com.neoutils.finsight.report

enum class ReportDocumentFormat(
    val fileExtension: String,
    val mimeType: String,
) {
    HTML(fileExtension = "html", mimeType = "text/html"),
    PDF(fileExtension = "pdf", mimeType = "application/pdf"),
}

data class ReportDocument(
    val fileNameWithoutExtension: String,
    val format: ReportDocumentFormat,
    val content: ByteArray,
) {
    val fileName: String
        get() = "${fileNameWithoutExtension.trim()}.${format.fileExtension}"
}
