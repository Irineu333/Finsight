package com.neoutils.finsight.feature.creditCards.modal.editInvoiceBalance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.core.analytics.Analytics
import com.neoutils.finsight.core.analytics.crashlytics.Crashlytics
import com.neoutils.finsight.core.ui.component.ModalManager
import com.neoutils.finsight.feature.creditCards.error.InvoiceError
import com.neoutils.finsight.feature.creditCards.event.AdjustInvoiceBalance
import com.neoutils.finsight.feature.creditCards.exception.InvoiceException
import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.creditCards.model.Invoice
import com.neoutils.finsight.feature.creditCards.repository.ICreditCardRepository
import com.neoutils.finsight.feature.creditCards.repository.IInvoiceRepository
import com.neoutils.finsight.feature.creditCards.usecase.AdjustInvoiceUseCase
import com.neoutils.finsight.feature.creditCards.usecase.CalculateInvoiceUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
class EditInvoiceBalanceViewModel(
    private val initialInvoiceId: Long,
    private val adjustInvoiceUseCase: AdjustInvoiceUseCase,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
    private val invoiceRepository: IInvoiceRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val timeZone get() = TimeZone.currentSystemDefault()
    private val currentDate get() = Clock.System.now().toLocalDateTime(timeZone).date

    private val selectedCreditCard = MutableStateFlow<CreditCard?>(null)
    private val selectedInvoice = MutableStateFlow<Invoice?>(null)

    private val editableInvoices = selectedCreditCard
        .filterNotNull()
        .map { creditCard ->
            invoiceRepository
                .getInvoicesByCreditCard(creditCard.id)
                .filter {
                    it.status.isEditable
                }
        }

    private val balance = selectedInvoice
        .filterNotNull()
        .map { invoice ->
            calculateInvoiceUseCase(invoice.id)
        }

    init {
        setup()
    }

    private fun setup() = viewModelScope.launch {
        val invoice = invoiceRepository.getInvoiceById(initialInvoiceId)

        if (invoice == null) {
            crashlytics.recordException(InvoiceException(InvoiceError.NotFound))
            modalManager.dismiss()
            return@launch
        }

        val creditCard = creditCardRepository.getCreditCardById(invoice.creditCardId)

        if (creditCard == null) {
            crashlytics.recordException(InvoiceException(InvoiceError.CreditCardNotFound))
            modalManager.dismiss()
            return@launch
        }

        selectedInvoice.value = invoice
        selectedCreditCard.value = creditCard
    }

    private val creditCards = flow {
        emit(creditCardRepository.getAllCreditCards())
    }

    val uiState = combine(
        creditCards,
        selectedCreditCard.filterNotNull(),
        editableInvoices,
        selectedInvoice.filterNotNull(),
        balance,
    ) { cards, selectedCard, invoices, selectedInvoice, balance ->
        EditInvoiceBalanceUiState.Content(
            creditCards = cards,
            selectedCreditCard = selectedCard,
            editableInvoices = invoices,
            selectedInvoice = selectedInvoice,
            currentBalance = balance,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EditInvoiceBalanceUiState.Loading,
    )

    fun onAction(action: EditInvoiceBalanceAction) = viewModelScope.launch {
        when (action) {
            is EditInvoiceBalanceAction.SelectCreditCard -> {
                selectedCreditCard.value = action.creditCard

                selectedInvoice.value = invoiceRepository
                    .getOpenInvoice(action.creditCard.id) ?: return@launch
            }

            is EditInvoiceBalanceAction.SelectInvoice -> {
                selectedInvoice.value = action.invoice
            }

            is EditInvoiceBalanceAction.Submit -> {
                submit(action.targetBalance)
            }
        }
    }

    private fun submit(targetBalance: Double) = viewModelScope.launch {
        val invoice = selectedInvoice.value ?: return@launch

        adjustInvoiceUseCase(
            invoice = invoice,
            target = targetBalance,
            adjustmentDate = currentDate,
        ).onLeft {
            crashlytics.recordException(it)
            modalManager.dismiss()
        }.onRight {
            analytics.logEvent(AdjustInvoiceBalance)
            modalManager.dismiss()
        }
    }
}
