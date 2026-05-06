@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.feature.transactions.modal.editTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Either.Companion.catch
import arrow.core.flatMap
import com.neoutils.finsight.core.analytics.Analytics
import com.neoutils.finsight.core.analytics.crashlytics.Crashlytics
import com.neoutils.finsight.feature.transactions.form.TransactionForm
import com.neoutils.finsight.feature.accounts.model.Account
import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.transactions.model.Transaction
import com.neoutils.finsight.core.ui.component.ModalManager
import com.neoutils.finsight.core.utils.extension.combine
import com.neoutils.finsight.feature.accounts.repository.IAccountRepository
import com.neoutils.finsight.feature.categories.repository.ICategoryRepository
import com.neoutils.finsight.feature.creditCards.model.InvoiceMonth
import com.neoutils.finsight.feature.creditCards.repository.ICreditCardRepository
import com.neoutils.finsight.feature.creditCards.repository.IInvoiceRepository
import com.neoutils.finsight.feature.transactions.event.EditTransaction
import com.neoutils.finsight.feature.transactions.repository.IOperationRepository
import com.neoutils.finsight.feature.transactions.repository.ITransactionRepository
import com.neoutils.finsight.feature.transactions.usecase.IBuildTransactionUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.YearMonth

class EditTransactionViewModel(
    private val transaction: Transaction,
    private val transactionRepository: ITransactionRepository,
    private val operationRepository: IOperationRepository,
    private val categoryRepository: ICategoryRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val accountRepository: IAccountRepository,
    private val buildTransactionUseCase: IBuildTransactionUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val selectedCreditCard = MutableStateFlow<CreditCard?>(null)
    private val selectedDueMonth = MutableStateFlow<YearMonth?>(null)
    private val selectedAccount = MutableStateFlow<Account?>(null)

    init {
        viewModelScope.launch {
            selectedCreditCard.value = transaction.creditCardId?.let { creditCardRepository.getCreditCardById(it) }
            selectedDueMonth.value = transaction.invoiceId?.let { invoiceRepository.getInvoiceById(it)?.dueMonth }
            selectedAccount.value = transaction.accountId?.let { accountRepository.getAccountById(it) }
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
    ) { categories, creditCards, invoices, accounts, selectedCard, dueMonth, account ->
        EditTransactionUiState(
            incomeCategories = categories.filter { it.type.isIncome },
            expenseCategories = categories.filter { it.type.isExpense },
            creditCards = creditCards,
            selectedCreditCard = selectedCard,
            invoiceSelection = dueMonth?.let { month ->
                InvoiceMonth(
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
        initialValue = EditTransactionUiState(),
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
        buildTransactionUseCase(
            form = form,
            id = transaction.id,
            operationId = transaction.operationId,
        ).flatMap {
            catch {
                transactionRepository.update(it)
                it.operationId?.let { operationId ->
                    operationRepository.updateOperation(operationId, it)
                }
            }
        }.onLeft {
            crashlytics.recordException(it)
        }.onRight {
            analytics.logEvent(EditTransaction(form))
            modalManager.dismissAll()
        }
    }
}
