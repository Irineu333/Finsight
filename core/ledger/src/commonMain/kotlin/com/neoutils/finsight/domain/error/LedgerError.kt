package com.neoutils.finsight.domain.error

import com.neoutils.finsight.domain.model.AccountType

sealed class LedgerError(val message: String) {
    data object Unbalanced : LedgerError("Transaction entries must sum to zero for every currency.")

    /**
     * A leg was tagged with a dimension its kind does not accept.
     *
     * Never reachable by a user action — it is a defect in the writer, surfaced
     * loudly precisely because its natural failure mode is silence: a misplaced
     * dimension produces no error, only sums that are quietly wrong.
     */
    data object MisplacedDimension :
        LedgerError("A dimension may only be carried by a leg of an account nature its kind accepts.")

    /** A paid invoice is settled history: nothing about it may change. */
    data object PaidInvoice : LedgerError("A paid invoice cannot be changed.")

    /**
     * A closed invoice takes no new spending, but it must still accept the payment
     * that settles it — the one transaction that is *about* closing the cycle.
     */
    data object ClosedInvoice : LedgerError("A closed invoice only accepts its own payment.")

    /**
     * A closed account keeps its history and takes no new movement.
     *
     * The invariant is one — it lives on the `Account` — but the account reached by
     * a write is one of three facades, and telling the user "this account is closed"
     * when what they picked was an archived *category* names the wrong thing. The
     * facade travels with the error so only the message varies, never the rule.
     */
    data class ClosedAccount(val facade: ClosedFacade) :
        LedgerError("A closed ${facade.name.lowercase()} cannot receive new entries.")

    /**
     * Removing movement from a closed *permanent* account is refused, mirroring
     * the precondition that let it close: `ArchiveAccountUseCase` only closes an
     * ASSET/LIABILITY at a zero balance, because archiving must not strand money.
     * A removal is the same event running backwards — it would reopen a balance on
     * an account that takes no new entries and appears in no selector, leaving the
     * user with a figure and no screen able to fix it.
     *
     * Temporary accounts (a category) are exempt for the same reason they archive
     * at any balance: their balance is a period total, not money sitting anywhere.
     */
    data class ClosedAccountRemoval(val facade: ClosedFacade) :
        LedgerError("Removing movement from a closed ${facade.name.lowercase()} would strand a balance.")
}

/**
 * Which facade of the chart of accounts an operation was refused on.
 *
 * Only the **monetary** facades appear here. Every rule that refuses something for
 * being closed exists to stop money being stranded, and only an ASSET/LIABILITY
 * can strand any — a category is never the reason a write or a removal is refused,
 * so naming it would be inventing a case that cannot happen.
 */
enum class ClosedFacade {
    ACCOUNT, CREDIT_CARD;

    companion object {
        /**
         * The facade a chart-of-accounts row projects onto, read from its type —
         * for the ledger side, where only the `AccountType` is known. The write
         * boundary derives the same thing from the user's own account-vs-card
         * choice, which is the intent, not the row.
         *
         * Callers reach this only for a permanent account (the closure rules do
         * not apply to any other), so EQUITY — the reconciliation row, which no
         * screen archives — reads as an account.
         */
        fun of(type: AccountType): ClosedFacade =
            if (type == AccountType.LIABILITY) CREDIT_CARD else ACCOUNT
    }
}

class UnbalancedTransactionException(val error: LedgerError) : Exception(error.message)

/** Raised at the write boundary when an invoice's status forbids the transaction. */
class InvoiceLockedException(val error: LedgerError) : Exception(error.message)

/** Raised at the write boundary when an account is closed to new movement. */
class ClosedAccountException(val error: LedgerError) : Exception(error.message)
