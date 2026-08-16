package com.neoutils.finsight.domain.error

import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.transaction_error_installment_share
import com.neoutils.finsight.resources.transaction_error_is_adjustment
import com.neoutils.finsight.resources.transaction_error_multiple_monetary_legs
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

    /**
     * The three reasons the rewrite shape cannot express a transaction, which is what
     * makes editing it impossible rather than merely unavailable. `Transaction.editObstacle`
     * is where they are decided, once, and both the screen and the surface read it.
     */
    MULTIPLE_MONETARY_LEGS(
        message = "The transaction has more than one monetary leg, so it cannot be edited",
    ),
    IS_ADJUSTMENT(message = "An adjustment is not edited as a transaction"),
    INSTALLMENT_SHARE(message = "One share of an installment is not edited on its own"),
}

fun TransactionError.toUiText() = when (this) {
    TransactionError.NOT_FOUND -> UiText.Res(Res.string.transaction_error_not_found)
    TransactionError.MULTIPLE_MONETARY_LEGS ->
        UiText.Res(Res.string.transaction_error_multiple_monetary_legs)

    TransactionError.IS_ADJUSTMENT -> UiText.Res(Res.string.transaction_error_is_adjustment)
    TransactionError.INSTALLMENT_SHARE ->
        UiText.Res(Res.string.transaction_error_installment_share)
}
