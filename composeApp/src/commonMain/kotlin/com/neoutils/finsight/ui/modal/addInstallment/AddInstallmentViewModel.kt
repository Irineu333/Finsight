package com.neoutils.finsight.ui.modal.addInstallment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.exception.InstallmentException
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.InvoiceMonthSelection
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.usecase.AddInstallmentUseCase
import com.neoutils.finsight.extension.toYearMonth
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.add_installment_error_generic
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.util.UiText
import kotlin.time.Clock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.YearMonth

class AddInstallmentViewModel(
    private val categoryRepository: ICategoryRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val addInstallmentUseCase: AddInstallmentUseCase,
    private val modalManager: ModalManager,
) : ViewModel() {

    private val selectedCreditCard = MutableStateFlow<CreditCard?>(null)
    private val selectedDueMonth = MutableStateFlow<YearMonth?>(null)

    private val _events = Channel<AddInstallmentEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val categories = categoryRepository.observeAllCategories()

    private val creditCards = creditCardRepository.observeAllCreditCards()

    private val invoices = selectedCreditCard.map { card ->
        if (card != null) {
            invoiceRepository.getInvoicesByCreditCard(card.id)
        } else {
            emptyList()
        }
    }

    val uiState = combine(
        categories,
        creditCards,
        invoices,
        selectedCreditCard,
        selectedDueMonth,
    ) { categories, creditCards, invoices, selectedCard, dueMonth ->
        AddInstallmentUiState(
            categories = categories.filter { it.type.isExpense },
            creditCards = creditCards,
            selectedCreditCard = selectedCard,
            invoiceSelection = dueMonth?.let { month ->
                InvoiceMonthSelection(
                    dueMonth = month,
                    existingInvoice = invoices.find { it.dueMonth == month },
                )
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AddInstallmentUiState(),
    )

    init {
        initialCreditCard()
    }

    private fun initialCreditCard() {
        viewModelScope.launch {
            val firstCard = creditCardRepository
                .getAllCreditCards()
                .firstOrNull() ?: return@launch

            selectCreditCard(firstCard)
        }
    }

    fun onAction(action: AddInstallmentAction) {
        when (action) {
            is AddInstallmentAction.SelectCreditCard -> selectCreditCard(action.creditCard)
            is AddInstallmentAction.NavigateToMonth -> selectedDueMonth.value = action.dueMonth
            is AddInstallmentAction.Submit -> submit(
                form = action.form,
                installments = action.installments,
            )
        }
    }

    private fun selectCreditCard(creditCard: CreditCard?) = viewModelScope.launch {
        selectedCreditCard.update { creditCard }

        selectedDueMonth.value = creditCard?.let {
            invoiceRepository
                .getInvoicesByCreditCard(creditCard.id)
                .firstOrNull { it.status.isOpen }
                ?.dueMonth
                ?: Clock.System.now().toYearMonth()
        }
    }

    private fun submit(
        form: TransactionForm,
        installments: Int,
    ) = viewModelScope.launch {
        addInstallmentUseCase(
            form = form,
            installments = installments,
        ).onLeft {
            _events.send(
                AddInstallmentEvent.ShowError(
                    when (it) {
                        is InstallmentException -> it.error.toUiText()
                        else -> UiText.Res(Res.string.add_installment_error_generic)
                    }
                )
            )
        }.onRight {
            modalManager.dismiss()
        }
    }
}
