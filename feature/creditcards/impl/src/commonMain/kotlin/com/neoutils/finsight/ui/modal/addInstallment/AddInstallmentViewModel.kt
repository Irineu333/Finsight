package com.neoutils.finsight.ui.modal.addInstallment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.exception.InstallmentException
import com.neoutils.finsight.domain.extension.requireCurrencyOf
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.InvoiceMonthSelection
import com.neoutils.finsight.domain.model.invoiceWindowFor
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.CreateInstallments
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.usecase.AddInstallmentUseCase
import com.neoutils.finsight.domain.usecase.ValidateTransactionFormUseCase
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.extension.today
import com.neoutils.finsight.util.dayMonthYear
import com.neoutils.finsight.extension.combine
import com.neoutils.finsight.extension.currentYearMonth
import com.neoutils.finsight.extension.toYearMonth
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.add_installment_error_generic
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.util.UiText
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.YearMonth

class AddInstallmentViewModel(
    private val categoryRepository: ICategoryRepository,
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val addInstallmentUseCase: AddInstallmentUseCase,
    private val validateTransactionForm: ValidateTransactionFormUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
    private val clock: Clock,
) : ViewModel() {

    /**
     * What the user is filling in. An instalment purchase is always an expense on a card, so
     * neither type nor target is a choice here — they are constants of this sheet.
     */
    private data class Input(
        val date: String,
        val title: String = "",
        val amount: String = "",
        val category: Category? = null,
        val installments: Int = 2,
    )

    private val input = MutableStateFlow(Input(date = dayMonthYear.format(clock.today())))

    private val selectedCreditCard = MutableStateFlow<CreditCard?>(null)
    private val selectedDueMonth = MutableStateFlow<YearMonth?>(null)


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
        input,
        categories,
        creditCards,
        invoices,
        selectedCreditCard,
        selectedDueMonth,
    ) { input, categories, creditCards, invoices, selectedCard, dueMonth ->

        val invoiceSelection = selectedCard?.let { card ->
            dueMonth?.let { month ->
                InvoiceMonthSelection(
                    creditCard = card,
                    dueMonth = month,
                    existingInvoice = invoices.find { it.dueMonth == month },
                )
            }
        }

        val form = input.toForm(
            creditCard = selectedCard,
            invoiceDueMonth = invoiceSelection?.dueMonth,
        )

        AddInstallmentUiState(
            form = form,
            today = clock.today(),
            // The invoice and the instalment count are this sheet's own conditions: the form
            // is a single expense as far as the rule is concerned, and two is what makes it
            // an instalment purchase at all.
            canSubmit = validateTransactionForm(form).isRight() &&
                invoiceSelection != null &&
                !invoiceSelection.isClosedToNewExpenses &&
                form.installments > 1,
            categories = categories.filter { it.type.isExpense },
            creditCards = creditCards,
            selectedCreditCard = selectedCard,
            currency = selectedCard?.let { accountRepository.requireCurrencyOf(it) },
            invoiceSelection = invoiceSelection,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AddInstallmentUiState(
            form = input.value.toForm(),
            today = clock.today(),
        ),
    )

    private fun Input.toForm(
        creditCard: CreditCard? = null,
        invoiceDueMonth: YearMonth? = null,
    ) = TransactionForm.from(
        type = TransactionType.EXPENSE,
        amount = amount,
        title = title,
        date = date,
        category = category,
        target = TransactionTarget.CREDIT_CARD,
        creditCard = creditCard,
        invoiceDueMonth = invoiceDueMonth,
        account = null,
        installments = installments,
    )

    init {
        initialCreditCard()

        // The invoice governs the date. This reads the card and the invoice and never the
        // date itself, which is what makes the reverse direction impossible rather than
        // merely avoided: editing the date has no path back to the invoice.
        viewModelScope.launch {
            combine(selectedCreditCard, selectedDueMonth, ::Pair)
                .collect { (creditCard, dueMonth) ->
                    if (creditCard == null || dueMonth == null) return@collect
                    placeDateInInvoiceWindow(creditCard, dueMonth)
                }
        }
    }

    /**
     * Moves the date into the window the selected invoice admits purchases in, keeping the
     * day and letting the window decide the month. The instalments are then laid out one
     * month apart from it, so the first one starts where its own invoice does.
     *
     * Never later than today: a future invoice is not a purchase in the future, it is one in
     * the present that falls due later. A date already inside the window comes back
     * unchanged, so the invoice selected on open leaves today alone.
     *
     * A date still being typed parses to nothing, and today's day stands in for it.
     */
    private fun placeDateInInvoiceWindow(creditCard: CreditCard, dueMonth: YearMonth) {
        val today = clock.today()

        val day = runCatching { dayMonthYear.parse(input.value.date) }
            .getOrNull()?.day ?: today.day

        val date = creditCard
            .invoiceWindowFor(dueMonth)
            .dateOn(day)
            .coerceAtMost(today)

        input.update { it.copy(date = dayMonthYear.format(date)) }
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
            is AddInstallmentAction.ChangeTitle -> input.update { it.copy(title = action.title) }
            is AddInstallmentAction.ChangeAmount -> input.update { it.copy(amount = action.amount) }
            is AddInstallmentAction.ChangeDate -> input.update { it.copy(date = action.date) }
            is AddInstallmentAction.ChangeInstallments -> input.update {
                // Two is the floor: one instalment is just an expense, and the sheet that
                // records those is another one.
                it.copy(installments = action.installments.coerceAtLeast(2))
            }

            is AddInstallmentAction.SelectCategory -> input.update { it.copy(category = action.category) }
            is AddInstallmentAction.SelectCreditCard -> selectCreditCard(action.creditCard)
            is AddInstallmentAction.NavigateToMonth -> selectedDueMonth.value = action.dueMonth
            is AddInstallmentAction.Submit -> submit()
        }
    }

    private fun selectCreditCard(creditCard: CreditCard?) = viewModelScope.launch {
        // Cleared first so that no pair of the new card with the old card's invoice is ever
        // observed: that pair names a window neither selection stands for, and the date
        // would be placed in it before being placed again in the right one.
        selectedDueMonth.value = null
        selectedCreditCard.update { creditCard }

        selectedDueMonth.value = creditCard?.let {
            invoiceRepository
                .getInvoicesByCreditCard(creditCard.id)
                .firstOrNull { it.status.isOpen }
                ?.dueMonth
                ?: clock.currentYearMonth()
        }
    }

    private fun submit() = viewModelScope.launch {
        val form = uiState.value.form
        val installments = form.installments

        addInstallmentUseCase(
            form = form,
            installments = installments,
        ).onLeft {
            crashlytics.recordException(it)
            modalManager.showError(
                when (it) {
                    is InstallmentException -> it.error.toUiText()
                    else -> UiText.Res(Res.string.add_installment_error_generic)
                }
            )
        }.onRight {
            analytics.logEvent(CreateInstallments(form, count = installments))
            modalManager.dismiss()
        }
    }
}
