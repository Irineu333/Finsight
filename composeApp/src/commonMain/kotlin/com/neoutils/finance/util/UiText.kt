package com.neoutils.finance.util

import org.jetbrains.compose.resources.StringResource

sealed class UiText {
    data class Raw(val value: String) : UiText()
    data class Res(val res: StringResource) : UiText()
}