package com.neoutils.finsight.domain.error

import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.ledger_error_unbalanced
import com.neoutils.finsight.util.UiText

sealed class LedgerError(val message: String) {
    data object Unbalanced : LedgerError("Operation entries must sum to zero for every currency.")
}

class UnbalancedOperationException(val error: LedgerError) : Exception(error.message)

fun LedgerError.toUiText() = when (this) {
    LedgerError.Unbalanced -> UiText.Res(Res.string.ledger_error_unbalanced)
}
