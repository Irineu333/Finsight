package com.neoutils.finsight.feature.transactions.extension

import com.neoutils.finsight.core.ui.util.UiText
import com.neoutils.finsight.feature.transactions.error.OperationError
import com.neoutils.finsight.feature.transactions.resources.Res
import com.neoutils.finsight.feature.transactions.resources.operation_error_not_found

fun OperationError.toUiText() = when (this) {
    OperationError.NOT_FOUND -> UiText.Res(Res.string.operation_error_not_found)
}
