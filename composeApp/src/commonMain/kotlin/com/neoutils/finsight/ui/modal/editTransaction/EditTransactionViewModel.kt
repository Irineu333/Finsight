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
            // TODO: register exception
        }.onRight {
            modalManager.dismissAll()
        }
    }
}
