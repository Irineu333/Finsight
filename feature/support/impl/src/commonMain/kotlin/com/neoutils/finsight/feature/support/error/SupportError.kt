package com.neoutils.finsight.feature.support.error

import com.neoutils.finsight.feature.support.resources.Res
import com.neoutils.finsight.feature.support.resources.support_error_empty_description
import com.neoutils.finsight.feature.support.resources.support_error_empty_message
import com.neoutils.finsight.feature.support.resources.support_error_empty_title
import com.neoutils.finsight.feature.support.resources.support_error_unknown
import com.neoutils.finsight.core.ui.util.UiText
enum class SupportError(val message: String) {
    EMPTY_TITLE(message = "Title cannot be empty."),
    EMPTY_DESCRIPTION(message = "Description cannot be empty."),
    EMPTY_MESSAGE(message = "Message cannot be empty."),
    UNKNOWN(message = "An unknown error occurred."),
}

fun SupportError.toUiText() = when (this) {
    SupportError.EMPTY_TITLE -> UiText.Res(Res.string.support_error_empty_title)
    SupportError.EMPTY_DESCRIPTION -> UiText.Res(Res.string.support_error_empty_description)
    SupportError.EMPTY_MESSAGE -> UiText.Res(Res.string.support_error_empty_message)
    SupportError.UNKNOWN -> UiText.Res(Res.string.support_error_unknown)
}
