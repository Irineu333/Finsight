@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.advancePayment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.error.ClosedAccountException
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.error.UnbalancedTransactionException
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.AdvanceInvoicePayment
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.usecase.AdvanceInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.SuggestCrossCurrencyAmountUseCase
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.ledger_action_error_generic
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.util.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val currentDate
    get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

class AdvancePaymentViewModel(
    private val invoiceId: Long,
    private val advanceInvoicePaymentUseCase: AdvanceInvoicePaymentUseCase,
    private val suggestCrossCurrencyAmount: SuggestCrossCurrencyAmountUseCase,
    private val invoiceRepository: IInvoiceRepository,
    private val accountRepository: IAccountRepository,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val selectedAccount = MutableStateFlow<Account?>(null)
    private val amount = MutableStateFlow(0.0)
    private val date = MutableStateFlow(currentDate)

    private val accounts = flow {
        emit(accountRepository.getAllAccounts())
    }

    /** The card's own currency — what the amount field, and the ceiling on it, are in. */
    private val cardCurrency = flow {
        val invoice = invoiceRepository.getInvoiceById(invoiceId)
        emit(invoice?.let { accountRepository.getAccountById(it.creditCard.accountId)?.currency })
    }

    val uiState = combine(
        accounts,
        selectedAccount,
        amount,
        date,
        cardCurrency,
    ) { accounts, account, amount, date, cardCurrency ->
        val selected = account ?: accounts.firstOrNull { it.isDefault }

        AdvancePaymentUiState(
            accounts = accounts,
            selectedAccount = selected,
            cardCurrency = cardCurrency,
            suggestion = if (cardCurrency != null && selected != null) {
                suggestCrossCurrencyAmount(
                    amount = amount,
                    from = cardCurrency,
                    to = selected.currency,
                    on = date,
                )
            } else {
                null
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AdvancePaymentUiState(),
    )

    fun onAction(action: AdvancePaymentAction) {
        when (action) {
            is AdvancePaymentAction.SelectAccount -> {
                selectedAccount.value = action.account
            }

            is AdvancePaymentAction.ChangeAmount -> {
                amount.value = action.amount
            }

            is AdvancePaymentAction.ChangeDate -> {
                date.value = action.date
            }

            is AdvancePaymentAction.Submit -> {
                submit(
                    amount = action.amount,
                    date = action.date,
                    account = action.account,
                    paidAmount = action.paidAmount,
                )
            }
        }
    }

    private fun submit(
        amount: Double,
        date: LocalDate,
        account: Account? = selectedAccount.value,
        paidAmount: Double,
    ) = viewModelScope.launch {
        advanceInvoicePaymentUseCase(
            invoiceId = invoiceId,
            amount = amount,
            date = date,
            account = account ?: checkNotNull(accountRepository.getDefaultAccount()),
            // Two numbers only where two numbers mean something; on a same-currency
            // payment what leaves the account *is* what settles the invoice.
            paidAmount = paidAmount.takeIf { uiState.value.isCrossCurrency },
        ).onLeft {
            crashlytics.recordException(it)
            modalManager.showError(it.toUiMessage())
        }.onRight {
            analytics.logEvent(AdvanceInvoicePayment)
            modalManager.dismissAll()
        }
    }

    private fun Throwable.toUiMessage(): UiText = when (this) {
        is ClosedAccountException -> error.toUiText()
        is InvoiceException -> error.toUiText()
        is UnbalancedTransactionException -> error.toUiText()
        else -> UiText.Res(Res.string.ledger_action_error_generic)
    }
}
