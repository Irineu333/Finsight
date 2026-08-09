package com.neoutils.finsight.ui.modal.recurringForm

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.TransactionTarget

data class RecurringFormUiState(
    val accounts: List<Account> = emptyList(),
    val selectedAccount: Account? = null,
    val creditCards: List<CreditCard> = emptyList(),
    val selectedCreditCard: CreditCard? = null,
    val incomeCategories: List<Category> = emptyList(),
    val expenseCategories: List<Category> = emptyList(),
    /**
     * The currency of the selected card, resolved by the view model from the card's
     * `LIABILITY` account — a `CreditCard` names its account and nothing else states a
     * currency (design D17).
     */
    val creditCardCurrency: String? = null,
) {
    val targets = listOf(TransactionTarget.ACCOUNT, TransactionTarget.CREDIT_CARD)

    /**
     * The currency the amount is typed in: that of what the template will post to — the
     * card when it targets one, the chosen account otherwise. The field's symbol is the
     * account's, never the device locale's (design D10, D17), and it changes on its own
     * when the target does.
     *
     * `null` while no target is chosen yet: nothing denominates the field, so it does
     * not format, and the form is not submittable in that state anyway.
     */
    fun currencyOf(target: TransactionTarget): String? = when (target) {
        TransactionTarget.CREDIT_CARD -> creditCardCurrency
        TransactionTarget.ACCOUNT -> selectedAccount?.currency
    }
}
