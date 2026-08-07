package com.neoutils.finsight.ui.modal.editTransaction

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.InvoiceMonthSelection
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.form.TransactionForm
import kotlinx.datetime.LocalDate

data class EditTransactionUiState(
    /** What the sheet renders and what the ViewModel would write — the same object. */
    val form: TransactionForm,
    /** Today as the app understands it, which bounds the date picker. */
    val today: LocalDate,
    /** Whether the form is worth submitting, decided where the clock is. */
    val canSubmit: Boolean = false,
    /**
     * The target the user picked, which the form normalises away for an income. Kept apart
     * so switching to income and back does not forget the card.
     */
    val selectedTarget: TransactionTarget = TransactionTarget.ACCOUNT,
    /**
     * The category the transaction already carries, resolved from the dimension of
     * its nominal leg. It arrives asynchronously, unlike the rest of the form's
     * seed values, because the ledger hands out an identity and the categories
     * feature turns it into a facade (design D6).
     */
    val transactionCategory: Category? = null,
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
