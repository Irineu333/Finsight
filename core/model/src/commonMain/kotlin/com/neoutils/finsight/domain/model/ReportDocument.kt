package com.neoutils.finsight.domain.model

data class ReportDocument(
    val fileNameWithoutExtension: String,
    val format: Format,
    val content: ByteArray,
) {
    val fileName: String
        get() = "${fileNameWithoutExtension.trim()}.${format.fileExtension}"

    enum class Format(
        val fileExtension: String,
        val mimeType: String,
    ) {
        HTML(fileExtension = "html", mimeType = "text/html"),
        PDF(fileExtension = "pdf", mimeType = "application/pdf"),
    }
}