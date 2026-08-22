@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.invoicePayment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.AdvanceInvoicePayment
import com.neoutils.finsight.domain.analytics.event.PayInvoice
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.error.ClosedAccountException
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.error.UnbalancedTransactionException
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.usecase.AdvanceInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CrossCurrencyAmountSuggestion
import com.neoutils.finsight.domain.usecase.PayInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.SuggestCrossCurrencyAmountUseCase
import com.neoutils.finsight.extension.combine
import com.neoutils.finsight.extension.today
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.ledger_action_error_generic
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.util.UiText
import com.neoutils.finsight.util.dayMonthYear
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Paying an invoice, whatever state it is in.
 *
 * The sheet **names** the invoice it pays instead of inheriting it from the screen that
 * opened it: card and invoice are chosen here, and [initialInvoiceId] is only a
 * pre-selection. What is owed is read from the invoice currently selected, because a
 * figure received ready-made would describe another invoice the moment the user switches.
 *
 * The state decides the mode — a part of what is owed, or the whole of it — and which use
 * case writes it. Neither is a flag on this class.
 */
class InvoicePaymentViewModel(
    private val initialInvoiceId: Long?,
    private val payInvoicePaymentUseCase: PayInvoicePaymentUseCase,
    private val advanceInvoicePaymentUseCase: AdvanceInvoicePaymentUseCase,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
    private val suggestCrossCurrencyAmount: SuggestCrossCurrencyAmountUseCase,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val accountRepository: IAccountRepository,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
    private val clock: Clock,
) : ViewModel() {

    private val currentDate get() = clock.today()

    private val selectedCreditCard = MutableStateFlow<CreditCard?>(null)
    private val selectedInvoiceId = MutableStateFlow<Long?>(null)
    private val selectedAccount = MutableStateFlow<Account?>(null)

    /** What the user stated, in the card's currency — only the partial mode reads it. */
    private val statedAmount = MutableStateFlow(0.0)

    /** The date as the form holds it: text, because that is what the field edits. */
    private val date = MutableStateFlow(dayMonthYear.format(currentDate))

    private val creditCards = creditCardRepository.observeAllCreditCards()

    /**
     * The card's invoices a payment may name. The filter is the domain's predicate, so a
     * future or already paid invoice is out by construction rather than by failing after
     * the user picked it.
     */
    private val payableInvoices = selectedCreditCard.flatMapLatest { creditCard ->
        creditCard
            ?.let { card ->
                invoiceRepository
                    .observeInvoicesByCreditCard(card.id)
                    .map { invoices -> invoices.filter(Invoice::acceptsPayment) }
            }
            ?: flowOf(emptyList())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    // Read reactively rather than resolved once: another screen may close the cycle
    // while this sheet is open, and the mode follows the invoice.
    private val selectedInvoice = selectedInvoiceId.flatMapLatest { id ->
        payableInvoices.map { invoices -> invoices.firstOrNull { it.id == id } }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    /**
     * What the selected invoice owes and the currency it owes in, emitted together with
     * the invoice they describe — three facts that must never be read out of step.
     */
    private val owed = selectedInvoice.map { invoice ->
        Owed(
            invoice = invoice,
            amount = invoice?.let { calculateInvoiceUseCase(it) } ?: 0.0,
            currency = invoice?.let {
                accountRepository.getAccountById(it.creditCard.accountId)?.currency
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    init {
        viewModelScope.launch {
            val initial = initialInvoiceId?.let { invoiceRepository.getInvoiceById(it) }
            val cards = creditCardRepository.getAllCreditCards()

            selectCreditCard(
                creditCard = initial
                    ?.let { invoice -> cards.firstOrNull { it.id == invoice.creditCard.id } }
                    ?: cards.firstOrNull(),
                preselected = initial?.takeIf { it.acceptsPayment }?.id,
            )
        }

        // The invoice governs the date. This collector reads the selected invoice and
        // never the date itself, which is what makes the reverse direction impossible
        // rather than merely avoided.
        viewModelScope.launch {
            selectedInvoice.collect { invoice ->
                invoice ?: return@collect
                date.value = dayMonthYear.format(settlementDateFor(invoice, date.value))
            }
        }
    }

    val uiState = combine(
        creditCards,
        selectedCreditCard,
        payableInvoices,
        accountRepository.observeAllAccounts(),
        selectedAccount,
        owed,
        date,
        statedAmount,
    ) { cards, selectedCard, invoices, accounts, account, owed, date, stated ->
        if (owed == null) return@combine InvoicePaymentUiState.Loading

        val content = InvoicePaymentUiState.Content(
            creditCards = cards,
            selectedCreditCard = selectedCard,
            invoices = invoices,
            selectedInvoice = owed.invoice,
            accounts = accounts,
            selectedAccount = account ?: accounts.firstOrNull { it.isDefault },
            outstandingDebt = owed.amount,
            invoiceCurrency = owed.currency,
            date = date,
            today = currentDate,
        )

        content.copy(suggestion = suggestionFor(content, stated))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InvoicePaymentUiState.Loading,
    )

    fun onAction(action: InvoicePaymentAction) {
        when (action) {
            is InvoicePaymentAction.SelectCreditCard -> viewModelScope.launch {
                selectCreditCard(action.creditCard)
            }

            is InvoicePaymentAction.SelectInvoice -> {
                selectedInvoiceId.value = action.invoice.id
            }

            is InvoicePaymentAction.SelectAccount -> {
                selectedAccount.value = action.account
            }

            is InvoicePaymentAction.ChangeAmount -> {
                statedAmount.value = action.amount
            }

            is InvoicePaymentAction.ChangeDate -> {
                date.value = action.date
            }

            is InvoicePaymentAction.Submit -> submit(
                amount = action.amount,
                paidAmount = action.paidAmount,
                account = action.account,
            )
        }
    }

    /**
     * What the archive implies leaves the paying account.
     *
     * It is asked what the **card's** side costs in the account's currency, never the
     * other way round: in the discharging mode that side is what is owed, otherwise it is
     * what the user stated. Both the mode and the disagreement between the two ends come
     * from the state, which is where they are decided.
     */
    private suspend fun suggestionFor(
        content: InvoicePaymentUiState.Content,
        stated: Double,
    ): CrossCurrencyAmountSuggestion? {
        if (!content.isCrossCurrency) return null

        val currency = content.invoiceCurrency ?: return null
        val payer = content.selectedAccount ?: return null

        return suggestCrossCurrencyAmount(
            amount = if (content.settles) content.outstandingDebt else stated,
            from = currency,
            to = payer.currency,
            on = runCatching { dayMonthYear.parse(content.date) }.getOrDefault(currentDate),
        )
    }

    /**
     * Card governs invoice: the invoice is **cleared first**, so that no pair of the new
     * card with the old card's invoice is ever observed — that pair names a window
     * neither selection stands for, and the date would be placed in it before being
     * placed again in the right one.
     */
    private suspend fun selectCreditCard(creditCard: CreditCard?, preselected: Long? = null) {
        selectedInvoiceId.value = null
        selectedCreditCard.value = creditCard
        selectedInvoiceId.value = preselected ?: creditCard?.let { card ->
            invoiceRepository
                .getInvoicesByCreditCard(card.id)
                .firstOrNull(Invoice::acceptsPayment)
                ?.id
        }
    }

    /**
     * Today's day placed in [invoice]'s settlement window.
     *
     * The day is what is preserved and the window decides the month, as the other card
     * forms do. Here the window is a **limit**: a date it does not admit is one the
     * domain would refuse, so it is pulled in rather than signalled.
     */
    private fun settlementDateFor(invoice: Invoice, from: String?): LocalDate {
        val today = currentDate
        val day = from?.let { runCatching { dayMonthYear.parse(it) }.getOrNull()?.day } ?: today.day
        return invoice.settlementWindow(today).dateOn(day)
    }

    private fun submit(
        amount: Double,
        paidAmount: Double,
        account: Account?,
    ) = viewModelScope.launch {
        val state = uiState.value as? InvoicePaymentUiState.Content ?: return@launch
        val invoice = state.selectedInvoice ?: return@launch
        val on = runCatching { dayMonthYear.parse(state.date) }.getOrNull() ?: return@launch
        val paying = account ?: accountRepository.getDefaultAccount() ?: return@launch

        // Two numbers only where two numbers mean something; on a same-currency payment
        // what leaves the account *is* what settles the invoice.
        val leaving = paidAmount.takeIf { state.isCrossCurrency }

        // The state chose the mode, so it chooses the use case and the event with it —
        // the two intentions stay distinguishable even behind a single door.
        val result = if (state.settles) {
            payInvoicePaymentUseCase(
                invoiceId = invoice.id,
                date = on,
                account = paying,
                paidAmount = leaving,
            )
        } else {
            advanceInvoicePaymentUseCase(
                invoiceId = invoice.id,
                amount = amount,
                date = on,
                account = paying,
                paidAmount = leaving,
            )
        }

        result.onLeft {
            crashlytics.recordException(it)
            modalManager.showError(it.toUiMessage())
        }.onRight {
            analytics.logEvent(if (state.settles) PayInvoice else AdvanceInvoicePayment)
            modalManager.dismissAll()
        }
    }

    private fun Throwable.toUiMessage(): UiText = when (this) {
        is ClosedAccountException -> error.toUiText()
        is InvoiceException -> error.toUiText()
        is UnbalancedTransactionException -> error.toUiText()
        else -> UiText.Res(Res.string.ledger_action_error_generic)
    }

    /** What one invoice owes, in the currency it owes it in, beside the invoice itself. */
    private data class Owed(
        val invoice: Invoice?,
        val amount: Double,
        val currency: String?,
    )
}
