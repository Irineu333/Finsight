package com.neoutils.finsight.ui.modal.createInvoice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.CreateInvoice
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.InvoiceMonthSelection
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.usecase.CreateInvoiceUseCase
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.ledger_action_error_generic
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.util.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.YearMonth

/**
 * Declares that a cycle existed. It carries the target and nothing else: what the invoice
 * is worth is a later gesture, and creating chains no form after itself.
 */
class CreateInvoiceViewModel(
    private val creditCard: CreditCard,
    initialDueMonth: YearMonth,
    invoiceRepository: IInvoiceRepository,
    private val createInvoiceUseCase: CreateInvoiceUseCase,
    private val onCreated: (Invoice) -> Unit,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val dueMonth = MutableStateFlow(initialDueMonth)

    private val invoices = invoiceRepository
        .observeInvoicesByCreditCard(creditCard.id)

    val uiState = combine(invoices, dueMonth) { invoices, month ->
        CreateInvoiceUiState(
            selection = InvoiceMonthSelection(
                creditCard = creditCard,
                dueMonth = month,
                existingInvoice = invoices.find { it.dueMonth == month },
            ),
            isLoaded = true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CreateInvoiceUiState(
            selection = InvoiceMonthSelection(
                creditCard = creditCard,
                dueMonth = initialDueMonth,
                existingInvoice = null,
            )
        )
    )

    fun onAction(action: CreateInvoiceAction) {
        when (action) {
            is CreateInvoiceAction.SelectDueMonth -> {
                dueMonth.value = action.dueMonth
            }

            CreateInvoiceAction.Submit -> submit()
        }
    }

    private fun submit() = viewModelScope.launch {
        createInvoiceUseCase(
            creditCard = creditCard,
            dueMonth = dueMonth.value,
        ).onLeft {
            crashlytics.recordException(it)
            modalManager.showError(it.toUiMessage())
        }.onRight { invoice ->
            analytics.logEvent(CreateInvoice)
            onCreated(invoice)
            modalManager.dismiss()
        }
    }

    private fun Throwable.toUiMessage(): UiText = when (this) {
        is InvoiceException -> error.toUiText()
        else -> UiText.Res(Res.string.ledger_action_error_generic)
    }
}
