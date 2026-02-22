@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.addTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Either.Companion.catch
import arrow.core.flatMap
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.InvoiceMonthSelection
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.domain.repository.*
import com.neoutils.finsight.domain.usecase.AddInstallmentUseCase
import com.neoutils.finsight.domain.usecase.BuildTransactionUseCase
import com.neoutils.finsight.extension.combine
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.YearMonth

class AddTransactionViewModel(
    private val categoryRepository: ICategoryRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val operationRepository: IOperationRepository,
    private val accountRepository: IAccountRepository,
    private val buildTransactionUseCase: BuildTransactionUseCase,
    private val addInstallmentUseCase: AddInstallmentUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    private val selectedCreditCard = MutableStateFlow<CreditCard?>(null)
    private val selectedDueMonth = MutableStateFlow<YearMonth?>(null)
    private val selectedAccount = MutableStateFlow<Account?>(null)

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
        accounts,
        invoices,
        selectedCreditCard,
        selectedDueMonth,
        selectedAccount,
    ) { categories, creditCards, accounts, invoices, selectedCard, dueMonth, account ->
        AddTransactionUiState(
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
        initialValue = AddTransactionUiState(),
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

    fun addTransaction(
        form: TransactionForm
    ) = viewModelScope.launch {
        if (form.installments > 1) {
            addInstallmentUseCase(
                form = form,
                installments = form.installments,
            ).onLeft {
                // TODO: register exception
            }.onRight {
                modalManager.dismiss()
            }

            return@launch
        }

        buildTransactionUseCase(form)
            .flatMap {
                catch {
                    operationRepository.createOperation(
                        kind = Operation.Kind.TRANSACTION,
                        title = it.title,
                        date = it.date,
                        categoryId = it.category?.id,
                        sourceAccountId = it.account?.id,
                        targetCreditCardId = it.creditCard?.id,
                        targetInvoiceId = it.invoice?.id,
                        transactions = listOf(it),
                    )
                }
            }.onLeft {
                // TODO: register exception
            }.onRight {
                modalManager.dismiss()
            }
    }
}
