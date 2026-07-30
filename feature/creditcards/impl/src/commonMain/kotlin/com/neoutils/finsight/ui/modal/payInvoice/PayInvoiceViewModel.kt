package com.neoutils.finsight.ui.modal.payInvoice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.error.ClosedAccountException
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.error.UnbalancedTransactionException
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.PayInvoice
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.usecase.PayInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.PayInvoiceUseCase
import com.neoutils.finsight.domain.usecase.SuggestConvertedAmountUseCase
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.ledger_action_error_generic
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.util.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class PayInvoiceViewModel(
    private val invoiceId: Long,
    private val payInvoicePaymentUseCase: PayInvoicePaymentUseCase,
    private val payInvoiceUseCase: PayInvoiceUseCase,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
    private val invoiceRepository: IInvoiceRepository,
    private val accountRepository: IAccountRepository,
    private val suggestConvertedAmount: SuggestConvertedAmountUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val selectedAccount = MutableStateFlow<Account?>(null)
    private val date = MutableStateFlow<LocalDate?>(null)

    val uiState = combine(
        accountRepository.observeAllAccounts(),
        selectedAccount,
        date,
    ) { accounts, account, date ->
        val selected = account ?: accounts.firstOrNull { it.isDefault }
        val invoice = invoiceRepository.getInvoiceById(invoiceId)
        val card = invoice?.creditCard

        PayInvoiceUiState(
            accounts = accounts,
            selectedAccount = selected,
            cardCurrency = card?.currency,
            // The known end is the debt, in the card's currency; what is suggested is the
            // other one — what has to leave the account to settle it.
            suggestion = if (selected != null && invoice != null && card != null && date != null) {
                suggestConvertedAmount(
                    fromCurrency = card.currency,
                    toCurrency = selected.currency,
                    amount = calculateInvoiceUseCase(invoice),
                    date = date,
                )
            } else {
                null
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PayInvoiceUiState(),
    )

    fun onAction(action: PayInvoiceAction) {
        when (action) {
            is PayInvoiceAction.SelectAccount -> {
                selectedAccount.value = action.account
            }

            is PayInvoiceAction.DateChanged -> {
                date.value = action.date
            }

            is PayInvoiceAction.Submit -> {
                submit(
                    date = action.date,
                    account = action.account,
                    accountAmount = action.accountAmount,
                )
            }
        }
    }

    private fun submit(
        date: LocalDate,
        account: Account? = selectedAccount.value,
        accountAmount: Double?,
    ) = viewModelScope.launch {
        // The screen holds an id; resolving it to the facade is its job, because the
        // ledger only knows the dimension the facade carries.
        val invoice = invoiceRepository.getInvoiceById(invoiceId) ?: return@launch
        val invoiceAmount = calculateInvoiceUseCase(invoice)

        // Bound to a `val`: `if (c) {..} else {..}.onLeft{}` attaches the chain to the
        // else branch alone, so the zero-amount path's result was silently dropped
        // (no error, no dismiss, no analytics).
        val result = if (invoiceAmount == 0.0) {
            payInvoiceUseCase(
                invoiceId = invoiceId,
                paidAt = date,
            )
        } else {
            payInvoicePaymentUseCase(
                invoiceId = invoiceId,
                date = date,
                account = account ?: checkNotNull(accountRepository.getDefaultAccount()),
                // Single currency: what leaves the account is the debt itself, and the
                // number comes from the same read the card side uses.
                accountAmount = accountAmount ?: invoiceAmount,
            )
        }

        result.onLeft {
            crashlytics.recordException(it)
            modalManager.showError(it.toUiMessage())
        }.onRight {
            analytics.logEvent(PayInvoice)
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
