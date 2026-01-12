package com.neoutils.finance.util

import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

sealed class UiText {
    data class Raw(val value: String) : UiText()
    data class Res(val res: StringResource) : UiText()

    suspend fun asString(): String = when (this) {
        is Raw -> value
        is Res -> getString(res)
    }
}
