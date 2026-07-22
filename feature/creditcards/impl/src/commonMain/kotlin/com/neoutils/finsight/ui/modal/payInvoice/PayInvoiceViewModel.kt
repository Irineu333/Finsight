package com.neoutils.finsight.ui.modal.payInvoice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.error.ClosedAccountException
import com.neoutils.finsight.domain.error.InvoiceLockedException
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
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val selectedAccount = MutableStateFlow<Account?>(null)

    val uiState = combine(
        accountRepository.observeAllAccounts(),
        selectedAccount,
    ) { accounts, account ->
        PayInvoiceUiState(
            accounts = accounts,
            selectedAccount = account ?: accounts.firstOrNull { it.isDefault },
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

            is PayInvoiceAction.Submit -> {
                submit(
                    date = action.date,
                    account = action.account,
                )
            }
        }
    }

    private fun submit(
        date: LocalDate,
        account: Account? = selectedAccount.value,
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
        is InvoiceLockedException -> error.toUiText()
        is UnbalancedTransactionException -> error.toUiText()
        else -> UiText.Res(Res.string.ledger_action_error_generic)
    }
}
