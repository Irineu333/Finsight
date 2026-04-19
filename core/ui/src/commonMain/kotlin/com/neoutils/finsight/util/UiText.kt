package com.neoutils.finsight.util

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

sealed class UiText {
    data class Raw(val value: String) : UiText()
    data class Res(val res: StringResource) : UiText()
    data class ResWithArgs(val res: StringResource, val args: List<Any>) : UiText() {
        constructor(res: StringResource, vararg args: Any) : this(res, args.toList())
    }

    suspend fun asString(): String = when (this) {
        is Raw -> value
        is Res -> getString(res)
        is ResWithArgs -> getString(res, formatArgs = args.toTypedArray())
    }
}

@Composable
fun stringUiText(error: UiText): String {
    return when (error) {
        is UiText.Raw -> error.value
        is UiText.Res -> stringResource(error.res)
        is UiText.ResWithArgs -> stringResource(error.res, formatArgs = error.args.toTypedArray())
    }
}
