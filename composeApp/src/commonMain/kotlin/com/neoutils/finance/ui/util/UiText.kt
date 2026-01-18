package com.neoutils.finance.ui.util

import androidx.compose.runtime.Composable
import com.neoutils.finance.util.UiText
import org.jetbrains.compose.resources.stringResource

@Composable
fun stringUiText(error: UiText): String {
    return when (error) {
        is UiText.Raw -> error.value
        is UiText.Res -> stringResource(error.res)
        is UiText.ResWithArgs -> stringResource(error.res, formatArgs = error.args.toTypedArray())
    }
}
