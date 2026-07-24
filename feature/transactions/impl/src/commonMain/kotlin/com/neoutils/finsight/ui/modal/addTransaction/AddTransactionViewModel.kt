@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.addTransaction

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
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.InvoiceMonthSelection
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.CreateInstallments
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.analytics.event.CreateTransaction
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
    private val transactionRepository: ITransactionRepository,
    private val accountRepository: IAccountRepository,
    private val buildTransactionUseCase: BuildTransactionUseCase,
    private val addInstallmentUseCase: AddInstallmentUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {



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

    private val categories = categoryRepository.observeAllCategories()

    private val creditCards = creditCardRepository.observeAllCreditCards()

    private val accounts = accountRepository.observeAllAccounts()

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

    fun onAction(action: AddTransactionAction) {
        when (action) {
            is AddTransactionAction.SelectCreditCard -> selectCreditCard(action.creditCard)
            is AddTransactionAction.SelectInvoiceMonth -> selectedDueMonth.value = action.dueMonth
            is AddTransactionAction.SelectAccount -> selectedAccount.value = action.account
            is AddTransactionAction.Submit -> submit(action.form)
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

        buildTransactionUseCase(form)
            .flatMap {
                catch {
                    transactionRepository.createTransaction(it)
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
        else -> UiText.Res(Res.string.transaction_error_generic)
    }
}
