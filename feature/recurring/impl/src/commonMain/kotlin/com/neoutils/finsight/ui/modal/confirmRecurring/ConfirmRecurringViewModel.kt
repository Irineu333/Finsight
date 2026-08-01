package com.neoutils.finsight.ui.modal.confirmRecurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.extension.currencyOf
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.ConfirmRecurring
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.analytics.event.SkipRecurring
import com.neoutils.finsight.domain.usecase.ConfirmRecurringUseCase
import com.neoutils.finsight.domain.usecase.SkipRecurringUseCase
import com.neoutils.finsight.extension.combine
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.retire_action_error_generic
import com.neoutils.finsight.util.UiText
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

class ConfirmRecurringViewModel(
    val recurring: Recurring,
    private val targetDate: LocalDate,
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val confirmRecurringUseCase: ConfirmRecurringUseCase,
    private val skipRecurringUseCase: SkipRecurringUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val currentDate get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    // Only an *open* account or card can be pre-selected. An archived one is still
    // shown on the recurring itself (that is its history), but the ledger refuses
    // to post to it, so here it leaves the selection empty and the user picks.
    private val initialAccount = recurring.account?.takeIf { !it.isArchived }
    private val initialCreditCard = recurring.creditCard?.takeIf { !it.isArchived }
    private val initialTarget = if (initialCreditCard != null) {
        TransactionTarget.CREDIT_CARD
    } else {
        TransactionTarget.ACCOUNT
    }
    /**
     * What the recurring's own amount is denominated in — the currency of the account or
     * card it names (design D17). `null` when it names neither, in which case there is
     * nothing to constrain and every account is offered.
     */
    private val recurringCurrency = recurring.account?.currency ?: recurring.creditCard?.currency

    private val confirmDate = MutableStateFlow(targetDate.takeIf { it <= currentDate } ?: currentDate)
    private val selectedTarget = MutableStateFlow(initialTarget)
    private val selectedAccount = MutableStateFlow(initialAccount)
    private val selectedCreditCard = MutableStateFlow(initialCreditCard)
    private val selectedInvoice = MutableStateFlow<Invoice?>(null)
    private val invoices = MutableStateFlow<List<Invoice>>(emptyList())

    init {
        viewModelScope.launch {
            selectedCreditCard.collectLatest { creditCard ->
                if (creditCard == null) {
                    invoices.value = emptyList()
                    selectedInvoice.value = null
                    return@collectLatest
                }

                val allInvoices = invoiceRepository.getInvoicesByCreditCard(creditCard.id)
                invoices.value = allInvoices
                selectedInvoice.value = allInvoices.firstOrNull { it.status.isOpen } ?: allInvoices.firstOrNull()
            }
        }
    }

    /**
     * The selected card's currency, read off the `LIABILITY` account it projects onto
     * (design D17). Resolved beside the card so the two cannot disagree.
     */
    private val creditCardCurrency = selectedCreditCard.map { card ->
        card?.let { accountRepository.currencyOf(it) }
    }

    val uiState = combine(
        confirmDate,
        selectedTarget,
        selectedAccount,
        selectedCreditCard,
        selectedInvoice,
        invoices,
        accountRepository.observeAllAccounts(),
        creditCardRepository.observeAllCreditCards(),
        creditCardCurrency,
    ) { date, target, account, creditCard, invoice, invoiceList, accounts, creditCards, cardCurrency ->
        // No fallback to the default account: substituting where the money moves
        // through is not a detail the app gets to decide in silence. With nothing
        // selected the modal keeps Confirm disabled until the user says where.
        val offeredAccounts = accounts.offeredFor(recurringCurrency) { it.currency }
        val offeredCreditCards = creditCards.offeredFor(recurringCurrency) { it.currency }

        ConfirmRecurringUiState(
            recurring = recurring,
            confirmDate = date,
            selectedTarget = target,
            accounts = offeredAccounts,
            // A silently shorter list is a lie by omission, so the modal says why —
            // for either list, since both shrink for the same reason.
            hiddenByCurrency = offeredAccounts.size < accounts.size ||
                offeredCreditCards.size < creditCards.size,
            recurringCurrency = recurringCurrency,
            selectedAccount = account,
            creditCards = offeredCreditCards,
            selectedCreditCard = creditCard,
            invoices = invoiceList,
            selectedInvoice = invoice,
            creditCardCurrency = cardCurrency,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ConfirmRecurringUiState(
            recurring = recurring,
            confirmDate = targetDate.takeIf { it <= currentDate } ?: currentDate,
            selectedTarget = initialTarget,
            selectedAccount = initialAccount,
            selectedCreditCard = initialCreditCard,
        ),
    )

    fun onAction(action: ConfirmRecurringAction) {
        when (action) {
            is ConfirmRecurringAction.TargetSelected -> {
                selectedTarget.value = action.target
                if (action.target.isCreditCard && selectedCreditCard.value == null) {
                    selectedCreditCard.value = uiState.value.creditCards.firstOrNull()
                }
            }

            is ConfirmRecurringAction.AccountSelected -> selectedAccount.value = action.account
            is ConfirmRecurringAction.CreditCardSelected -> selectedCreditCard.value = action.creditCard
            is ConfirmRecurringAction.DateChanged -> {
                confirmDate.value = action.date.takeIf { it <= currentDate } ?: currentDate
            }

            is ConfirmRecurringAction.InvoiceSelected -> selectedInvoice.value = action.invoice
            is ConfirmRecurringAction.Confirm -> confirm(action.amount)
            is ConfirmRecurringAction.Skip -> skip()
        }
    }

    private fun confirm(amount: String) = viewModelScope.launch {
        val date = confirmDate.value.takeIf { it <= currentDate } ?: currentDate

        val parsedAmount = amount.filter { it.isDigit() }
            .toLongOrNull()
            ?.toDouble()
            ?.div(100)
            ?: recurring.amount

        confirmRecurringUseCase(
            recurring = recurring,
            date = date,
            amount = parsedAmount,
            target = uiState.value.selectedTarget,
            account = if (uiState.value.selectedTarget.isAccount) uiState.value.selectedAccount else null,
            creditCard = if (uiState.value.selectedTarget.isCreditCard) uiState.value.selectedCreditCard else null,
            invoice = if (uiState.value.selectedTarget.isCreditCard) uiState.value.selectedInvoice else null,
        ).onLeft {
            crashlytics.recordException(it)
            modalManager.showError(UiText.Res(Res.string.retire_action_error_generic))
        }.onRight {
            analytics.logEvent(ConfirmRecurring(recurring, uiState.value.selectedTarget))
            modalManager.dismiss()
        }
    }

    private fun skip() = viewModelScope.launch {
        val date = confirmDate.value.takeIf { it <= currentDate } ?: currentDate
        skipRecurringUseCase(
            recurring = recurring,
            date = date,
        ).onLeft {
            crashlytics.recordException(it)
            modalManager.showError(UiText.Res(Res.string.retire_action_error_generic))
        }.onRight {
            analytics.logEvent(SkipRecurring(recurring, uiState.value.selectedTarget))
            modalManager.dismiss()
        }
    }
}

/**
 * The destinations a confirmation may be pointed at, given what the recurring's amount is
 * denominated in (design D17).
 *
 * A selector offers only what the domain accepts — D5's doctrine applied to the UI, and
 * what `TransferBetweenAccountsModal` already practises by leaving the source out of the
 * destinations. Redirecting a confirmation to an account or a card of another currency
 * would write the raw number as if it were that currency, so the refusal is prevented in
 * the control instead of reported as an error; the domain guard stays as a net, and is
 * never the designed path.
 *
 * **Accounts and cards go through the same function because they are the same rule.** They
 * had two copies of it and the card's copy was missing, which is the shape this kind of
 * omission takes: the domain refuses a card of another currency exactly as it refuses an
 * account, so an unfiltered card list offers what would be rejected. It matters more for
 * cards, in fact — choosing the card target auto-selects the first one, so the app can
 * land on the wrong currency without the user touching the selector at all.
 *
 * A recurring that names neither account nor card has nothing to constrain, and everything
 * is offered.
 */
internal fun <T> List<T>.offeredFor(currency: String?, currencyOf: (T) -> String?): List<T> =
    if (currency == null) this else filter { currencyOf(it) == currency }
