package com.neoutils.finsight.ui.modal.viewTransaction

import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.transaction_card_payment
import com.neoutils.finsight.resources.view_transaction_title_balance_adjustment
import com.neoutils.finsight.resources.view_transaction_title_invoice_adjustment
import com.neoutils.finsight.resources.view_transaction_title_transfer
import org.jetbrains.compose.resources.StringResource

/**
 * How an operation with neither title nor category is named.
 *
 * It is a fact of the operation's **form**, not a reserve literal standing in for an
 * absence: a transfer is between accounts, a payment settles an invoice, and an
 * adjustment corrects a balance or an invoice. The two header lines are read as one
 * sentence, so what this returns says what the nature above it did not.
 *
 * An expense and an income have no such name — their form is exhausted by their
 * nature — and `null` is the header omitting the line rather than inventing one.
 *
 * [isCardTarget] is the ledger's own fact, the presence of a liability leg, and it is
 * what separates the two adjustments. It is read for no other nature, because no
 * other nature is named by where it landed.
 *
 * Top-level and `internal` so the rule can be exercised without a screen.
 */
internal fun fallbackTitleFor(
    label: TransactionLabel,
    isCardTarget: Boolean,
): StringResource? = when (label) {
    TransactionLabel.PAYMENT -> Res.string.transaction_card_payment
    TransactionLabel.TRANSFER -> Res.string.view_transaction_title_transfer
    TransactionLabel.ADJUSTMENT -> if (isCardTarget) {
        Res.string.view_transaction_title_invoice_adjustment
    } else {
        Res.string.view_transaction_title_balance_adjustment
    }

    TransactionLabel.EXPENSE, TransactionLabel.INCOME -> null
}
