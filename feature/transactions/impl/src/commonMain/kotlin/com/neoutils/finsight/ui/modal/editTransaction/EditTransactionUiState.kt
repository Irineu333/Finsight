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
    /**
     * The currency of the selected card, resolved by the view model from the card's
     * `LIABILITY` account — a `CreditCard` names its account and nothing else states a
     * currency (design D17).
     */
    val creditCardCurrency: String? = null,
) {
    val targets = listOf(TransactionTarget.ACCOUNT, TransactionTarget.CREDIT_CARD)

    val isInvoiceBlocked = invoiceSelection?.isClosedToNewExpenses == true

    val categories = when {
        form.type.isIncome -> incomeCategories
        form.type.isExpense -> expenseCategories
        else -> emptyList()
    }

    /**
     * The currency the amount is typed in: that of whatever the form will write to —
     * the card when the expense targets one, the chosen account otherwise. The field's
     * symbol is therefore the account's, never the device locale's (design D10), and it
     * changes on its own when the target does.
     *
     * `null` while no target is chosen yet: nothing denominates the field, so it does
     * not format, and the form's own validation already refuses that state.
     */
    fun currencyOf(target: TransactionTarget): String? = when (target) {
        TransactionTarget.CREDIT_CARD -> creditCardCurrency
        TransactionTarget.ACCOUNT -> selectedAccount?.currency
    }
}
