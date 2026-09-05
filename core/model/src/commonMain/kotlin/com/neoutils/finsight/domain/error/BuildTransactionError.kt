package com.neoutils.finsight.domain.error

import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.build_transaction_error_account_required
import com.neoutils.finsight.resources.build_transaction_error_amount_not_positive
import com.neoutils.finsight.resources.build_transaction_error_amount_required
import com.neoutils.finsight.resources.build_transaction_error_closed_invoice
import com.neoutils.finsight.resources.build_transaction_error_closed_selection
import com.neoutils.finsight.resources.build_transaction_error_credit_card_expense_only
import com.neoutils.finsight.resources.build_transaction_error_credit_card_required
import com.neoutils.finsight.resources.build_transaction_error_date_future
import com.neoutils.finsight.resources.build_transaction_error_date_invalid
import com.neoutils.finsight.resources.build_transaction_error_date_required
import com.neoutils.finsight.resources.build_transaction_error_invoice_required
import com.neoutils.finsight.resources.build_transaction_error_recurring_month_locked
import com.neoutils.finsight.resources.build_transaction_error_title_or_category_required
import com.neoutils.finsight.util.UiText

/**
 * Why a [com.neoutils.finsight.domain.model.form.TransactionForm] cannot become a
 * transaction — the one vocabulary for it.
 *
 * Both readings of the question answer in these terms: the form asking *may I offer the
 * submit* and the build asking *may I write this*. They used to be two rule sets, one on
 * the form as a boolean and one here, free to drift apart.
 */
sealed class BuildTransactionError(val message: String) {

    data object AmountRequired : BuildTransactionError(
        message = "Amount is required."
    )

    data object AmountNotPositive : BuildTransactionError(
        message = "Amount must be greater than zero."
    )

    data object DateRequired : BuildTransactionError(
        message = "Date is required."
    )

    data object DateFuture : BuildTransactionError(
        message = "Date cannot be in the future."
    )

    data object RecurringMonthLocked : BuildTransactionError(
        message = "Recurring transactions cannot be moved to another month."
    )

    data object TitleOrCategoryRequired : BuildTransactionError(
        message = "Title or category is required."
    )

    data object CreditCardExpenseOnly : BuildTransactionError(
        message = "Only expenses can be associated with credit cards."
    )

    data object InvoiceRequired : BuildTransactionError(
        message = "Invoice is required for credit card transactions."
    )

    data object CreditCardRequired : BuildTransactionError(
        message = "Credit card is required."
    )

    data object AccountRequired : BuildTransactionError(
        message = "Account is required for account transactions."
    )

    data object ClosedInvoice : BuildTransactionError(
        message = "Cannot add transactions to closed invoices."
    )

    data object DateInvalid : BuildTransactionError(
        message = "Date is not a date."
    )

    /**
     * An archived account or card is still shown on a transaction being edited — that is its
     * history — but nothing new can be written to one, so the form declines rather than
     * letting the write boundary refuse (`LedgerError.ClosedAccount`).
     */
    data object ClosedSelection : BuildTransactionError(
        message = "An archived account or credit card cannot take a new transaction."
    )
}

fun BuildTransactionError.toUiText(): UiText = when (this) {
    BuildTransactionError.AmountRequired -> UiText.Res(Res.string.build_transaction_error_amount_required)
    BuildTransactionError.AmountNotPositive -> UiText.Res(Res.string.build_transaction_error_amount_not_positive)
    BuildTransactionError.DateRequired -> UiText.Res(Res.string.build_transaction_error_date_required)
    BuildTransactionError.DateInvalid -> UiText.Res(Res.string.build_transaction_error_date_invalid)
    BuildTransactionError.DateFuture -> UiText.Res(Res.string.build_transaction_error_date_future)
    BuildTransactionError.RecurringMonthLocked -> UiText.Res(Res.string.build_transaction_error_recurring_month_locked)
    BuildTransactionError.TitleOrCategoryRequired -> UiText.Res(Res.string.build_transaction_error_title_or_category_required)
    BuildTransactionError.CreditCardExpenseOnly -> UiText.Res(Res.string.build_transaction_error_credit_card_expense_only)
    BuildTransactionError.InvoiceRequired -> UiText.Res(Res.string.build_transaction_error_invoice_required)
    BuildTransactionError.CreditCardRequired -> UiText.Res(Res.string.build_transaction_error_credit_card_required)
    BuildTransactionError.AccountRequired -> UiText.Res(Res.string.build_transaction_error_account_required)
    BuildTransactionError.ClosedInvoice -> UiText.Res(Res.string.build_transaction_error_closed_invoice)
    BuildTransactionError.ClosedSelection -> UiText.Res(Res.string.build_transaction_error_closed_selection)
}
