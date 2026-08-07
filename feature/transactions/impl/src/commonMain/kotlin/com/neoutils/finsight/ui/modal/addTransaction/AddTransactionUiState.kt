package com.neoutils.finsight.ui.modal.addTransaction

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.InvoiceMonthSelection
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.form.TransactionForm
import kotlinx.datetime.LocalDate

data class AddTransactionUiState(
    /**
     * What the sheet renders and what the ViewModel would write — the same object, so
     * the two cannot disagree. Every field the user types lives here; the sheet holds
     * only the `TextFieldState`s that Compose needs to edit them.
     */
    val form: TransactionForm,
    /**
     * Today, as the app understands it. The date picker is bounded by it for the same reason
     * the form refuses a later date — a transaction is not recorded in the future — and both
     * now read one clock instead of the sheet reaching for its own.
     */
    val today: LocalDate,
    /**
     * Whether the form is worth submitting, decided where the clock is. [TransactionForm.isValid]
     * needs a today, and the ViewModel is the layer that has one — the sheet used to inject a
     * `Clock` of its own to answer this.
     */
    val canSubmit: Boolean = false,
    /**
     * The target the user picked, which the form normalises away for an income (an income
     * always lands on an account). Kept apart so switching to income and back does not
     * silently forget that they had chosen the card.
     */
    val selectedTarget: TransactionTarget = TransactionTarget.ACCOUNT,
    val incomeCategories: List<Category> = emptyList(),
    val expenseCategories: List<Category> = emptyList(),
    val creditCards: List<CreditCard> = emptyList(),
    val selectedCreditCard: CreditCard? = null,
    val invoiceSelection: InvoiceMonthSelection? = null,
    val accounts: List<Account> = emptyList(),
    val selectedAccount: Account? = null,
) {
    val targets = listOf(TransactionTarget.ACCOUNT, TransactionTarget.CREDIT_CARD)

    val isInvoiceBlocked = invoiceSelection?.isClosedToNewExpenses == true

    val categories = when {
        form.type.isIncome -> incomeCategories
        form.type.isExpense -> expenseCategories
        else -> emptyList()
    }
}
