package com.neoutils.finsight.domain.error

import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.transaction_error_not_found
import com.neoutils.finsight.util.UiText

enum class TransactionError(val message: String) {

    /**
     * The identity handed to the operation matches no transaction. The transaction is
     * resolved when the operation runs, so this is the refusal a caller gets for an
     * identifier that was never valid — and for one that stopped being valid between
     * the moment it was read and the moment it was used.
     */
    NOT_FOUND(message = "Transaction not found"),
}

fun TransactionError.toUiText() = when (this) {
    TransactionError.NOT_FOUND -> UiText.Res(Res.string.transaction_error_not_found)
}
