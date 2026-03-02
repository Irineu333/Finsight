package com.neoutils.finsight.ui.modal.recurringForm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.usecase.SaveRecurringUseCase
import com.neoutils.finsight.extension.combine
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RecurringFormViewModel(
    val recurring: Recurring?,
    private val categoryRepository: ICategoryRepository,
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val saveRecurringUseCase: SaveRecurringUseCase,
    private val modalManager: ModalManager,
) : ViewModel() {

    private val type = MutableStateFlow(recurring?.type ?: Transaction.Type.EXPENSE)
    private val selectedCategory = MutableStateFlow(recurring?.category)
    private val selectedAccount = MutableStateFlow(recurring?.account)
    private val selectedCreditCard = MutableStateFlow(recurring?.creditCard)

    private val categories = flow { emit(categoryRepository.getAllCategories()) }
    private val accounts = flow { emit(accountRepository.getAllAccounts()) }
    private val creditCards = flow { emit(creditCardRepository.getAllCreditCards()) }

    val uiState = combine(
        type,
        selectedCategory,
        selectedAccount,
        selectedCreditCard,
        categories,
        accounts,
        creditCards,
    ) { type, category, account, creditCard, cats, accs, cards ->
        RecurringFormUiState(
            type = type,
            isEditing = recurring != null,
            accounts = accs,
            selectedAccount = account ?: accs.firstOrNull { it.isDefault },
            creditCards = if (type.isIncome) emptyList() else cards,
            selectedCreditCard = if (type.isIncome) null else creditCard,
            incomeCategories = cats.filter { it.type == Category.Type.INCOME },
            expenseCategories = cats.filter { it.type == Category.Type.EXPENSE },
            selectedCategory = category,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RecurringFormUiState(
            type = recurring?.type ?: Transaction.Type.EXPENSE,
            isEditing = recurring != null,
            selectedAccount = recurring?.account,
            selectedCreditCard = recurring?.creditCard,
            selectedCategory = recurring?.category,
        ),
    )

    fun onAction(action: RecurringFormAction, amount: String, title: String, dayOfMonth: String) {
        when (action) {
            is RecurringFormAction.TypeChanged -> {
                type.value = action.type
                selectedCategory.value = selectedCategory.value?.takeIf {
                    (action.type.isIncome && it.type == Category.Type.INCOME) ||
                            (action.type.isExpense && it.type == Category.Type.EXPENSE)
                }
            }
            is RecurringFormAction.CategorySelected -> selectedCategory.value = action.category
            is RecurringFormAction.AccountSelected -> selectedAccount.value = action.account
            is RecurringFormAction.CreditCardSelected -> selectedCreditCard.value = action.creditCard
            is RecurringFormAction.Save -> save(amount, title, dayOfMonth, action.target)
        }
    }

    private fun save(amount: String, title: String, dayOfMonth: String, target: Transaction.Target) = viewModelScope.launch {
        saveRecurringUseCase(
            id = recurring?.id ?: 0L,
            type = type.value,
            amount = amount,
            title = title.ifEmpty { null },
            dayOfMonth = dayOfMonth,
            category = selectedCategory.value,
            account = if (target.isAccount) (selectedAccount.value ?: uiState.value.selectedAccount) else null,
            creditCard = if (target.isCreditCard) selectedCreditCard.value else null,
            createdAt = recurring?.createdAt,
            lastHandledYearMonth = recurring?.lastHandledYearMonth,
        ).onRight {
            modalManager.dismissAll()
        }
    }
}
