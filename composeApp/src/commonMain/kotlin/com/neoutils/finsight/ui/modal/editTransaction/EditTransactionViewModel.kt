@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.editTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Either.Companion.catch
import arrow.core.flatMap
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.InvoiceMonthSelection
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.exception.BuildTransactionException
import com.neoutils.finsight.domain.model.form.TransactionForm
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
    private val operationRepository: IOperationRepository,
    private val categoryRepository: ICategoryRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val accountRepository: IAccountRepository,
    private val buildTransactionUseCase: BuildTransactionUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    private val selectedCreditCard = MutableStateFlow(transaction.creditCard)
    private val selectedDueMonth = MutableStateFlow(transaction.invoice?.dueMonth)
    private val selectedAccount = MutableStateFlow(transaction.account)
    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage = _errorMessage.asSharedFlow()

    private val invoices = selectedCreditCard.map { card ->
        if (card != null) {
            invoiceRepository.getInvoicesByCreditCard(card.id)
        } else {
            emptyList()
        }
    }

    private val categories = flow {
        emit(categoryRepository.getAllCategories())
    }

    private val creditCards = flow {
        emit(creditCardRepository.getAllCreditCards())
    }

    private val accounts = flow {
        emit(accountRepository.getAllAccounts())
    }

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
        initialValue = EditTransactionUiState(
            selectedCreditCard = transaction.creditCard,
            selectedAccount = transaction.account,
            invoiceSelection = transaction.invoice?.let {
                InvoiceMonthSelection(
                    dueMonth = it.dueMonth,
                    existingInvoice = it
                )
            }
        )
    )

    fun selectCreditCard(creditCard: CreditCard?) = viewModelScope.launch {
        selectedCreditCard.value = creditCard
        selectedDueMonth.value = creditCard?.let {
            invoiceRepository
                .getInvoicesByCreditCard(creditCard.id)
                .firstOrNull { it.status.isOpen }
                ?.dueMonth
        }
    }

    fun navigateToMonth(dueMonth: YearMonth) {
        selectedDueMonth.value = dueMonth
    }

    fun selectAccount(account: Account?) {
        selectedAccount.value = account
    }

    fun updateTransaction(
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
        }.onLeft { throwable ->
            val message = (throwable as? BuildTransactionException)
                ?.error
                ?.message
                ?: throwable.message
                ?: "Failed to update transaction."
            _errorMessage.emit(message)
        }.onRight {
            modalManager.dismissAll()
        }
    }
}
