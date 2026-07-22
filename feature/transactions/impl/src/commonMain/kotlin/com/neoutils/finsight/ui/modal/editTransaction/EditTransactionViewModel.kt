@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.editTransaction

import com.neoutils.finsight.domain.error.ClosedAccountException
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.error.UnbalancedTransactionException
import com.neoutils.finsight.domain.error.toUiText
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
import com.neoutils.finsight.domain.exception.BuildTransactionException
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.EditTransaction
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.repository.*
import com.neoutils.finsight.domain.usecase.BuildTransactionUseCase
import com.neoutils.finsight.extension.combine
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.YearMonth

class EditTransactionViewModel(
    private val transaction: Transaction,
    private val transactionRepository: ITransactionRepository,
    private val categoryRepository: ICategoryRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val accountRepository: IAccountRepository,
    private val buildTransactionUseCase: BuildTransactionUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {



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
                transactionCategory.value = categoryRepository.getAllCategoriesIncludingClosed()
                    .firstOrNull { it.dimensionId == dimensionId }
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

    private val categories = categoryRepository.observeAllCategories()

    private val creditCards = creditCardRepository.observeAllCreditCards()

    private val accounts = accountRepository.observeAllAccounts()

    val uiState = combine(
        categories,
        creditCards,
        invoices,
        accounts,
        selectedCreditCard,
        selectedDueMonth,
        selectedAccount,
        transactionCategory,
    ) { categories, creditCards, invoices, accounts, selectedCard, dueMonth, account, category ->
        EditTransactionUiState(
            transactionCategory = category,
            incomeCategories = categories.filter { it.type.isIncome },
            expenseCategories = categories.filter { it.type.isExpense },
            creditCards = creditCards,
            selectedCreditCard = selectedCard,
            invoiceSelection = dueMonth?.let { month ->
                InvoiceMonthSelection(
                    dueMonth = month,
                    existingInvoice = invoices.find { it.dueMonth == month }
                )
            },
            accounts = accounts,
            selectedAccount = account ?: accounts.firstOrNull { it.isDefault },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EditTransactionUiState(selectedAccount = transaction.sourceAccount)
    )

    fun onAction(action: EditTransactionAction) {
        when (action) {
            is EditTransactionAction.SelectCreditCard -> selectCreditCard(action.creditCard)
            is EditTransactionAction.SelectInvoiceMonth -> selectedDueMonth.value = action.dueMonth
            is EditTransactionAction.SelectAccount -> selectedAccount.value = action.account
            is EditTransactionAction.Submit -> submit(action.form)
        }
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

    private fun submit(
        form: TransactionForm
    ) = viewModelScope.launch {
        buildTransactionUseCase(form).flatMap { intent ->
            catch {
                transactionRepository.updateTransaction(
                    id = transaction.id,
                    title = intent.title,
                    date = intent.date,
                    leg = intent.legs.first(),
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
