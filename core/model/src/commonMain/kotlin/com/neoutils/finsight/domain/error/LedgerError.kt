package com.neoutils.finsight.domain.error

import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.ledger_error_closed_invoice
import com.neoutils.finsight.resources.ledger_error_paid_invoice
import com.neoutils.finsight.resources.ledger_error_unbalanced
import com.neoutils.finsight.util.UiText

sealed class LedgerError(val message: String) {
    data object Unbalanced : LedgerError("Operation entries must sum to zero for every currency.")

    /** A paid invoice is settled history: nothing about it may change. */
    data object PaidInvoice : LedgerError("A paid invoice cannot be changed.")

    /**
     * A closed invoice takes no new spending, but it must still accept the payment
     * that settles it — the one operation that is *about* closing the cycle.
     */
    data object ClosedInvoice : LedgerError("A closed invoice only accepts its own payment.")
}

class UnbalancedOperationException(val error: LedgerError) : Exception(error.message)

/** Raised at the write boundary when an invoice's status forbids the operation. */
class InvoiceLockedException(val error: LedgerError) : Exception(error.message)

fun LedgerError.toUiText() = when (this) {
    LedgerError.Unbalanced -> UiText.Res(Res.string.ledger_error_unbalanced)
    LedgerError.PaidInvoice -> UiText.Res(Res.string.ledger_error_paid_invoice)
    LedgerError.ClosedInvoice -> UiText.Res(Res.string.ledger_error_closed_invoice)
}
