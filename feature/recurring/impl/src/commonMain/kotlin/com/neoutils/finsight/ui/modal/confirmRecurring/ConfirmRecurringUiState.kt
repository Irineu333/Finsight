package com.neoutils.finsight.ui.modal.confirmRecurring

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionTarget
import kotlinx.datetime.LocalDate

data class ConfirmRecurringUiState(
    val recurring: Recurring,
    val confirmDate: LocalDate,
    val selectedTarget: TransactionTarget = TransactionTarget.ACCOUNT,
    val accounts: List<Account> = emptyList(),
    val selectedAccount: Account? = null,
    val creditCards: List<CreditCard> = emptyList(),
    val selectedCreditCard: CreditCard? = null,
    val invoices: List<Invoice> = emptyList(),
    val selectedInvoice: Invoice? = null,
    /**
     * The currency of the selected card, resolved by the view model from the card's
     * `LIABILITY` account — a `CreditCard` names its account and nothing else states a
     * currency (design D17).
     */
    val creditCardCurrency: String? = null,
) {
    val targets = listOf(TransactionTarget.ACCOUNT, TransactionTarget.CREDIT_CARD)

    /**
     * The currency the amount is confirmed in: that of where the money will actually
     * move — the card when the confirmation targets one, the chosen account otherwise.
     *
     * It is the *selected* target and not the recurring's own, deliberately: redirecting
     * a confirmation to an account of another currency is what design D17 refuses, and
     * the field showing the destination's symbol is how the user sees it coming.
     *
     * `null` while nothing is selected — the state in which Confirm is already refused.
     */
    val currency: String? get() = when (selectedTarget) {
        TransactionTarget.CREDIT_CARD -> creditCardCurrency
        TransactionTarget.ACCOUNT -> selectedAccount?.currency
    }
}
