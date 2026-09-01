@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.editTransaction

import com.neoutils.finsight.domain.error.ClosedAccountException
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.error.UnbalancedTransactionException
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.extension.currencyOf
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.transaction_error_generic
import com.neoutils.finsight.util.UiText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Either.Companion.catch
import arrow.core.flatMap
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.InvoiceMonthSelection
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.EditTransaction
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.repository.*
import com.neoutils.finsight.domain.usecase.BuildTransactionUseCase
import com.neoutils.finsight.domain.usecase.ValidateTransactionFormUseCase
import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.extension.combine
import kotlin.math.roundToLong
import com.neoutils.finsight.extension.deriveTransactionType
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

class EditTransactionViewModel(
    private val transaction: Transaction,
    private val transactionRepository: ITransactionRepository,
    private val categoryRepository: ICategoryRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val accountRepository: IAccountRepository,
    private val buildTransactionUseCase: BuildTransactionUseCase,
    private val validateTransactionForm: ValidateTransactionFormUseCase,
    private val formatter: CurrencyFormatter,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
    private val clock: Clock,
) : ViewModel() {

    /**
     * What the user is editing, seeded from the transaction as the ledger holds it: the money
     * leg's direction says which type it is, and whether that leg is the card's says which
     * target.
     */
    private data class Input(
        val type: TransactionType,
        val target: TransactionTarget,
        val title: String,
        val amount: String,
        val date: String,
        val category: Category? = null,
    )

    private val input = MutableStateFlow(
        Input(
            type = transaction.primaryEntry
                ?.let { deriveTransactionType(it.amount, transaction.entries) }
                ?: TransactionType.EXPENSE,
            target = if (transaction.hasLiabilityLeg) {
                TransactionTarget.CREDIT_CARD
            } else {
                TransactionTarget.ACCOUNT
            },
            title = transaction.title.orEmpty(),
            // The seed reads in the currency the transaction was **recorded** in — its
            // own money leg's account. A transaction with no money leg states no
            // currency, so its digits stand undressed rather than borrowing one.
            amount = transaction.primaryEntry
                ?.let { formatter.format(transaction.amount, it.currency) }
                ?: (transaction.amount * 100).roundToLong().toString(),
            date = dayMonthYear.format(transaction.date),
        )
    )

    // The ledger gives an account id and a dimension; the facades behind them are
    // resolved once, here, because that is a lookup only these features can do.
    private val selectedCreditCard = MutableStateFlow<CreditCard?>(null)
    private val selectedDueMonth = MutableStateFlow<YearMonth?>(null)
    private val selectedAccount = MutableStateFlow(transaction.sourceAccount)
    private val transactionCategory = MutableStateFlow<Category?>(null)

    init {
        viewModelScope.launch {
            transaction.liabilityAccountId?.let { accountId ->
                selectedCreditCard.value = creditCardRepository.getAllCreditCardsIncludingClosed()
                    .firstOrNull { it.accountId == accountId }
            }
            transaction.liabilityDimensionId?.let { dimensionId ->
                selectedDueMonth.value = invoiceRepository.getAllInvoices()
                    .firstOrNull { it.dimensionId == dimensionId }
                    ?.dueMonth
            }
            transaction.nominalDimensionId?.let { dimensionId ->
                val category = categoryRepository.getCategoryByDimensionId(dimensionId)
                transactionCategory.value = category
                // Seeds the form, and only that: the category arrives a beat after the sheet
                // opens (design D6), so a choice already made in the meantime wins.
                input.update { if (it.category == null) it.copy(category = category) else it }
            }
        }
    }

    private val invoices = selectedCreditCard.map { card ->
        if (card != null) {
            invoiceRepository.getInvoicesByCreditCard(card.id)
        } else {
            emptyList()
        }
    }

    /**
     * The selected card's currency, asked of the rule that owns it (design D17). `null`
     * while no card is selected, which is what leaves the amount undenominated until one
     * is.
     */
    private val creditCardCurrency = selectedCreditCard.map { card ->
        card?.let { accountRepository.currencyOf(it) }
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
        // Declared after `creditCards` on purpose: properties initialise in declaration order,
        // so an init block above it would read an uninitialised field.
        //
        // With a single card there is nothing to choose, so aiming at the card target chooses it.
        viewModelScope.launch {
            combine(input.map { it.target }.distinctUntilChanged(), creditCards, ::Pair)
                .collect { (target, creditCards) ->
                    if (target.isCreditCard && creditCards.size == 1 && selectedCreditCard.value == null) {
                        selectCreditCard(creditCards.first())
                    }
                }
        }
    }

    val uiState = combine(
        input,
        categories,
        creditCards,
        invoices,
        accounts,
        selectedCreditCard,
        selectedDueMonth,
        selectedAccount,
        transactionCategory,
        creditCardCurrency,
    ) { input, categories, creditCards, invoices, accounts, selectedCard, dueMonth, account, category, cardCurrency ->

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

        EditTransactionUiState(
            form = form,
            today = clock.today(),
            canSubmit = validateTransactionForm(form).isRight() &&
                invoiceSelection?.isClosedToNewExpenses != true,
            selectedTarget = input.target,
            transactionCategory = category,
            incomeCategories = categories.filter { it.type.isIncome },
            expenseCategories = categories.filter { it.type.isExpense },
            creditCards = creditCards,
            selectedCreditCard = selectedCard,
            invoiceSelection = invoiceSelection,
            accounts = accounts,
            selectedAccount = effectiveAccount,
            creditCardCurrency = cardCurrency,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EditTransactionUiState(
            form = input.value.toForm(account = transaction.sourceAccount),
            today = clock.today(),
            selectedAccount = transaction.sourceAccount,
        )
    )

    fun onAction(action: EditTransactionAction) {
        when (action) {
            is EditTransactionAction.ChangeType -> changeType(action.type)
            is EditTransactionAction.ChangeTarget -> input.update { it.copy(target = action.target) }
            is EditTransactionAction.ChangeTitle -> input.update { it.copy(title = action.title) }
            is EditTransactionAction.ChangeAmount -> input.update { it.copy(amount = action.amount) }
            is EditTransactionAction.ChangeDate -> input.update { it.copy(date = action.date) }
            is EditTransactionAction.SelectCategory -> input.update { it.copy(category = action.category) }
            is EditTransactionAction.SelectCreditCard -> selectCreditCard(action.creditCard)
            is EditTransactionAction.SelectInvoiceMonth -> selectedDueMonth.value = action.dueMonth
            is EditTransactionAction.SelectAccount -> selectedAccount.value = action.account
            is EditTransactionAction.Submit -> submit()
        }
    }

    /** A category the new type does not accept is dropped: it cannot describe the transaction. */
    private fun changeType(type: TransactionType) = input.update {
        it.copy(
            type = type,
            category = it.category?.takeIf { category -> category.type.isAccept(type) },
        )
    }

    private fun selectCreditCard(creditCard: CreditCard?) = viewModelScope.launch {
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
        account: com.neoutils.finsight.domain.model.Account? = null,
    ) = TransactionForm.from(
        type = type,
        amount = amount,
        title = title,
        date = date,
        category = category,
        target = target,
        creditCard = creditCard,
        invoiceDueMonth = invoiceDueMonth,
        account = account,
    )

    private fun submit() = viewModelScope.launch {
        val form = uiState.value.form

        buildTransactionUseCase(form).flatMap { intent ->
            catch {
                transactionRepository.updateTransaction(
                    id = transaction.id,
                    title = intent.title,
                    date = intent.date,
                    legs = intent.legs,
                    contra = intent.contra,
                )
            }
        }.onLeft {
            crashlytics.recordException(it)
            modalManager.showError(it.toUiMessage())
        }.onRight {
            analytics.logEvent(EditTransaction(form))
            modalManager.dismissAll()
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
        else -> UiText.Res(Res.string.transaction_error_generic)
    }
}
