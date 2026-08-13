@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.addTransaction

import com.neoutils.finsight.domain.error.ClosedAccountException
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.error.UnbalancedTransactionException
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.exception.RecurringException
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.transaction_error_generic
import com.neoutils.finsight.util.UiText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Either.Companion.catch
import arrow.core.flatMap
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.InvoiceMonthSelection
import com.neoutils.finsight.domain.model.invoiceWindowFor
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.CreateInstallments
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.analytics.event.CreateTransaction
import com.neoutils.finsight.domain.repository.*
import com.neoutils.finsight.domain.usecase.AddInstallmentUseCase
import com.neoutils.finsight.domain.usecase.BuildTransactionUseCase
import com.neoutils.finsight.domain.usecase.StartRecurringFromTransactionUseCase
import com.neoutils.finsight.domain.usecase.ValidateTransactionFormUseCase
import com.neoutils.finsight.extension.combine
import com.neoutils.finsight.extension.isAccept
import com.neoutils.finsight.extension.today
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.util.dayMonthYear
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.YearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class AddTransactionViewModel(
    private val categoryRepository: ICategoryRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val transactionRepository: ITransactionRepository,
    private val accountRepository: IAccountRepository,
    private val buildTransactionUseCase: BuildTransactionUseCase,
    private val addInstallmentUseCase: AddInstallmentUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
    private val validateTransactionForm: ValidateTransactionFormUseCase,
    private val startRecurringFromTransaction: StartRecurringFromTransactionUseCase,
    private val clock: Clock,
) : ViewModel() {

    /**
     * What the user is filling in, before it is a form.
     *
     * One flow rather than one per field: nothing reads these apart — they exist to be
     * assembled into a [TransactionForm] and judged as a whole.
     */
    private data class Input(
        val date: String,
        val type: TransactionType = TransactionType.EXPENSE,
        val target: TransactionTarget = TransactionTarget.ACCOUNT,
        val title: String = "",
        val amount: String = "",
        val category: Category? = null,
        val installments: Int = 1,
        val isRecurring: Boolean = false,
    )

    private val input = MutableStateFlow(Input(date = dayMonthYear.format(clock.today())))

    private val selectedCreditCard = MutableStateFlow<CreditCard?>(null)
    private val selectedDueMonth = MutableStateFlow<YearMonth?>(null)
    private val selectedAccount = MutableStateFlow<Account?>(null)

    private val invoices = selectedCreditCard.map { card ->
        if (card != null) {
            invoiceRepository.getInvoicesByCreditCard(card.id)
        } else {
            emptyList()
        }
    }

    /**
     * The selected card's currency, read off the `LIABILITY` account it projects onto:
     * the card facade names the account, and the account is the only place a currency is
     * stated (design D17). Resolved here, beside the card, so the two cannot disagree.
     */
    private val creditCardCurrency = selectedCreditCard.map { card ->
        card?.let { accountRepository.getAccountById(it.accountId)?.currency }
    }

    private val categories = categoryRepository.observeAllCategories()

    private val creditCards = creditCardRepository
        .observeAllCreditCards()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    private val accounts = accountRepository.observeAllAccounts()

    init {
        // With a single card there is nothing to choose, so aiming at the card target
        // chooses it. It belongs here and not in the sheet: it is a decision about state,
        // and it also settles which invoice the expense lands on.
        viewModelScope.launch {
            combine(input.map { it.target }.distinctUntilChanged(), creditCards, ::Pair)
                .collect { (target, creditCards) ->
                    if (target.isCreditCard && creditCards.size == 1 && selectedCreditCard.value == null) {
                        selectCreditCard(creditCards.first())
                    }
                }
        }

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
     * day and letting the window decide the month.
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

    val uiState = combine(
        input,
        categories,
        creditCards,
        accounts,
        invoices,
        selectedCreditCard,
        selectedDueMonth,
        selectedAccount,
        creditCardCurrency,
    ) { input, categories, creditCards, accounts, invoices, selectedCard, dueMonth, account, cardCurrency ->

        // The account nobody chose is the default one, and the form has to see the same
        // account the selector shows — otherwise a submit writes to neither.
        val effectiveAccount = account ?: accounts.firstOrNull { it.isDefault }

        val invoiceSelection = selectedCard?.let { card ->
            dueMonth?.let { month ->
                InvoiceMonthSelection(
                    creditCard = card,
                    dueMonth = month,
                    existingInvoice = invoices.find { it.dueMonth == month }
                )
            }
        }

        val form = input.toForm(
            creditCard = selectedCard,
            invoiceDueMonth = invoiceSelection?.dueMonth,
            account = effectiveAccount,
        )

        AddTransactionUiState(
            form = form,
            today = clock.today(),
            // The rule is the use case's, and it carries its own clock — there is no today to
            // pass and no way to pass the wrong one. What is decided here is only the part the
            // form cannot see: whether the invoice it points at still takes spending.
            canSubmit = validateTransactionForm(form).isRight() &&
                invoiceSelection?.isClosedToNewExpenses != true,
            selectedTarget = input.target,
            incomeCategories = categories.filter { it.type.isIncome },
            expenseCategories = categories.filter { it.type.isExpense },
            creditCards = creditCards,
            selectedCreditCard = selectedCard,
            invoiceSelection = invoiceSelection,
            accounts = accounts,
            selectedAccount = effectiveAccount,
            creditCardCurrency = cardCurrency,
            isRecurring = input.isRecurring,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AddTransactionUiState(
            form = input.value.toForm(),
            today = clock.today(),
        ),
    )

    fun onAction(action: AddTransactionAction) {
        when (action) {
            is AddTransactionAction.ChangeType -> changeType(action.type)
            is AddTransactionAction.ChangeTarget -> input.update { it.copy(target = action.target) }
            is AddTransactionAction.ChangeTitle -> input.update { it.copy(title = action.title) }
            is AddTransactionAction.ChangeAmount -> input.update { it.copy(amount = action.amount) }
            is AddTransactionAction.ChangeDate -> input.update { it.copy(date = action.date) }
            is AddTransactionAction.ChangeInstallments -> changeInstallments(action.installments)
            is AddTransactionAction.ChangeRecurring -> {
                input.update { it.copy(isRecurring = action.isRecurring) }
            }

            is AddTransactionAction.SelectCategory -> input.update { it.copy(category = action.category) }
            is AddTransactionAction.SelectCreditCard -> selectCreditCard(action.creditCard)
            is AddTransactionAction.SelectInvoiceMonth -> selectedDueMonth.value = action.dueMonth
            is AddTransactionAction.SelectAccount -> selectedAccount.value = action.account
            is AddTransactionAction.Submit -> submit()
        }
    }

    /**
     * Splitting into instalments drops the recurring mark rather than hiding it: paying
     * in instalments is already a repetition, and a mark the sheet no longer shows must
     * not survive to the submit — the same reason [changeType] discards a category the
     * new type does not accept.
     */
    private fun changeInstallments(installments: Int) = input.update {
        it.copy(
            installments = installments,
            isRecurring = it.isRecurring && installments == 1,
        )
    }

    /**
     * A category the new type does not accept is dropped rather than hidden: an expense
     * category cannot describe an income, and keeping it would have the selector showing
     * one thing while the form carries another.
     */
    private fun changeType(type: TransactionType) = input.update {
        it.copy(
            type = type,
            category = it.category?.takeIf { category -> category.type.isAccept(type) },
        )
    }

    private fun selectCreditCard(creditCard: CreditCard?) = viewModelScope.launch {
        // Cleared first so that no pair of the new card with the old card's invoice is ever
        // observed: that pair names a window neither selection stands for, and the date
        // would be placed in it before being placed again in the right one.
        selectedDueMonth.value = null
        selectedCreditCard.value = creditCard
        selectedDueMonth.value = creditCard?.let {
            invoiceRepository
                .getInvoicesByCreditCard(creditCard.id)
                .firstOrNull { it.status.isOpen }
                ?.dueMonth
        }
    }

    private fun Input.toForm(
        creditCard: CreditCard? = null,
        invoiceDueMonth: YearMonth? = null,
        account: Account? = null,
    ) = TransactionForm.from(
        type = type,
        amount = amount,
        title = title,
        date = date,
        category = category,
        target = target,
        creditCard = creditCard,
        invoiceDueMonth = invoiceDueMonth,
        installments = installments,
        account = account,
    )

    private fun submit() = viewModelScope.launch {
        val form = uiState.value.form

        if (form.installments > 1) {
            addInstallmentUseCase(
                form = form,
                installments = form.installments,
            ).onLeft {
                crashlytics.recordException(it)
            }.onRight {
                analytics.logEvent(CreateInstallments(form, count = form.installments))
                modalManager.dismiss()
            }

            return@launch
        }

        // Read off the input rather than the state: the state is derived and lands a
        // frame later, and a submit right after the toggle would read the old answer.
        val isRecurring = input.value.isRecurring

        buildTransactionUseCase(form)
            .flatMap { intent ->
                if (isRecurring) {
                    // The intent is completed, never rebuilt: it already carries the
                    // invoice the user picked, and how a recurring is born is the
                    // recurring feature's rule, consumed here.
                    startRecurringFromTransaction(
                        form = form.asRecurringOn(intent.date),
                        firstCycle = intent,
                    )
                } else {
                    catch {
                        transactionRepository.createTransaction(intent)
                    }
                }
            }.onLeft {
                crashlytics.recordException(it)
                modalManager.showError(it.toUiMessage())
            }.onRight {
                analytics.logEvent(CreateTransaction(form))
                modalManager.dismiss()
            }
    }

    /**
     * The write boundary rejects with a typed error; without this the rejection
     * reached crashlytics and the user saw a modal that simply refused to close.
     */
    private fun Throwable.toUiMessage(): UiText = when (this) {
        is InvoiceException -> error.toUiText()
        is ClosedAccountException -> error.toUiText()
        is UnbalancedTransactionException -> error.toUiText()
        is RecurringException -> error.toUiText()
        else -> UiText.Res(Res.string.transaction_error_generic)
    }
}
