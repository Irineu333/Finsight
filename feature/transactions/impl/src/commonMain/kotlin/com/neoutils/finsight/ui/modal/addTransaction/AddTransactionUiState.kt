package com.neoutils.finsight.ui.modal.addTransaction

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.InvoiceMonthSelection
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.util.dayMonthYear
import kotlinx.datetime.LocalDate

/** What the date field explains about itself, when it has anything to explain. */
sealed interface DateSupport {
    /** The date sits outside the period the selected invoice admits purchases in. */
    data object OutsideInvoice : DateSupport

    /** The day of the month this transaction will repeat on. */
    data class RepeatsOnDay(val day: Int) : DateSupport
}

/**
 * The day a date being typed would repeat on, or `null` while it is not a date yet.
 * The field is edited character by character, so half of what passes through here
 * parses to nothing.
 */
private fun repeatDay(date: String): Int? =
    runCatching { dayMonthYear.parse(date) }.getOrNull()?.day

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
    /**
     * The currency of the selected card, resolved by the view model from the card's
     * `LIABILITY` account — a `CreditCard` names its account and nothing else states a
     * currency (design D17).
     */
    val creditCardCurrency: String? = null,
    /**
     * Whether this transaction is to become the first cycle of a recurring. Form state
     * only — nothing exists until it is saved.
     */
    val isRecurring: Boolean = false,
) {
    val targets = listOf(TransactionTarget.ACCOUNT, TransactionTarget.CREDIT_CARD)

    val isInvoiceBlocked = invoiceSelection?.isClosedToNewExpenses == true

    /**
     * Whether the date sits outside the period the selected invoice admits — something the
     * sheet says and never corrects. Decided here rather than in the composable, for the
     * same reason [canSubmit] is: a decision reachable only through a device is a decision
     * no test can reach.
     */
    val isDateOutsideInvoice = invoiceSelection?.diverges(form.date) == true

    /**
     * Whether the transaction may be marked as recurring at all.
     *
     * Instalments are the only thing in the way: paying in instalments is already a
     * repetition, and the two together would describe two of them over one purchase.
     * Nothing else is checked here — every valid, unsplit form yields a valid template,
     * so [canSubmit] already answers the rest.
     */
    val canRepeat = form.installments == 1

    /**
     * What the date field says beneath itself, decided here for the same reason
     * [canSubmit] is.
     *
     * The invoice warning wins: it tells the user something they may not want, while
     * the repetition note only echoes what they just chose.
     */
    val dateSupport: DateSupport? = when {
        isDateOutsideInvoice -> DateSupport.OutsideInvoice
        isRecurring -> repeatDay(form.date)?.let(DateSupport::RepeatsOnDay)
        else -> null
    }

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
