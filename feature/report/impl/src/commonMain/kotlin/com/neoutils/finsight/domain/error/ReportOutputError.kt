package com.neoutils.finsight.domain.error

import com.neoutils.finsight.feature.report.impl.resources.Res
import com.neoutils.finsight.feature.report.impl.resources.report_output_error_io_error
import com.neoutils.finsight.feature.report.impl.resources.report_output_error_unknown
import com.neoutils.finsight.feature.report.impl.resources.report_output_error_unsupported_format
import com.neoutils.finsight.util.UiText

enum class ReportOutputError(val message: String) {
    UNSUPPORTED_FORMAT("Report format not supported"),
    IO_ERROR("Failed to process the report"),
    UNKNOWN("An unexpected error occurred"),
}

fun ReportOutputError.toUiText() = when (this) {
    ReportOutputError.UNSUPPORTED_FORMAT -> UiText.Res(Res.string.report_output_error_unsupported_format)
    ReportOutputError.IO_ERROR -> UiText.Res(Res.string.report_output_error_io_error)
    ReportOutputError.UNKNOWN -> UiText.Res(Res.string.report_output_error_unknown)
}
