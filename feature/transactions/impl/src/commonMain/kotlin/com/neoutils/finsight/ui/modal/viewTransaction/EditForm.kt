package com.neoutils.finsight.ui.modal.viewTransaction

import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.feature.accounts.api.AccountsEntry
import com.neoutils.finsight.feature.creditcards.api.CreditCardsEntry
import com.neoutils.finsight.ui.component.Modal
import com.neoutils.finsight.ui.modal.editTransaction.EditTransactionModal

/**
 * Which form corrects an operation.
 *
 * It follows from what the operation **is** — its nature, derived from the legs — and
 * from nothing else: not from a count of legs, and not as a side effect of some other
 * rule. A transfer states two accounts and two amounts; a payment states a card, an
 * invoice and the account that pays it; the transaction form states a type, a target and
 * a category, and none of those exists on either.
 *
 * The two forms that are not this feature's are reached through the **public entry
 * point** of the feature that owns them, because one implementation may not name
 * another.
 *
 * Top-level and `internal` so the rule can be exercised without a screen.
 */
internal fun editFormFor(
    transaction: Transaction,
    accountsEntry: AccountsEntry,
    creditCardsEntry: CreditCardsEntry,
): Modal = when (transaction.label) {
    TransactionLabel.TRANSFER -> accountsEntry.editTransferModal(transaction)
    TransactionLabel.PAYMENT -> creditCardsEntry.editInvoicePaymentModal(transaction)
    else -> EditTransactionModal(transaction)
}
