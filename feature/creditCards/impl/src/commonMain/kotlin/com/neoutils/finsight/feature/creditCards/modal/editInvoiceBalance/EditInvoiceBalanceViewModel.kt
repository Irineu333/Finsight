package com.neoutils.finsight.feature.creditCards.modal.editInvoiceBalance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.core.analytics.Analytics
import com.neoutils.finsight.core.analytics.crashlytics.Crashlytics
import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.creditCards.model.Invoice
import com.neoutils.finsight.core.ui.component.ModalManager
import com.neoutils.finsight.feature.creditCards.event.AdjustInvoiceBalance
import com.neoutils.finsight.feature.creditCards.repository.ICreditCardRepository
import com.neoutils.finsight.feature.creditCards.repository.IInvoiceRepository
import com.neoutils.finsight.feature.creditCards.usecase.AdjustInvoiceUseCase
import com.neoutils.finsight.feature.creditCards.usecase.CalculateInvoiceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EditInvoiceBalanceViewModel(
    private val initialInvoice: Invoice,
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

    init {
        viewModelScope.launch {
            selectedCreditCard.value = creditCardRepository.getCreditCardById(initialInvoice.creditCardId)
        }
    }

    private val editableInvoices = selectedCreditCard
        .filterNotNull()
        .flatMapLatest { creditCard ->
            flow {
                emit(
                    invoiceRepository
                        .getInvoicesByCreditCard(creditCard.id)
                        .filter { it.status.isEditable }
                )
            }
        }

    private val creditCards = flow {
        emit(creditCardRepository.getAllCreditCards())
    }

    private val selectedInvoice = MutableStateFlow(initialInvoice)

    private val currentBalance = selectedInvoice.map { invoice ->
        calculateInvoiceUseCase(invoice.id)
    }.stateIn(
        scope = viewModelScope,
        initialValue = null,
        started = SharingStarted.WhileSubscribed(5_000),
    )

    val uiState = combine(
        creditCards,
        selectedCreditCard.filterNotNull(),
        editableInvoices,
        selectedInvoice,
        currentBalance,
    ) { cards, selectedCard, invoices, selectedInvoice, balance ->
        if (balance == null) {
            EditInvoiceBalanceUiState.Loading
        } else {
            EditInvoiceBalanceUiState.Content(
                creditCards = cards,
                selectedCreditCard = selectedCard,
                editableInvoices = invoices,
                selectedInvoice = selectedInvoice,
                currentBalance = balance
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EditInvoiceBalanceUiState.Loading
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
        adjustInvoiceUseCase(
            invoice = selectedInvoice.value,
            target = targetBalance,
            adjustmentDate = currentDate
        ).onLeft {
            crashlytics.recordException(it)
            modalManager.dismiss()
        }.onRight {
            analytics.logEvent(AdjustInvoiceBalance)
            modalManager.dismiss()
        }
    }
}
