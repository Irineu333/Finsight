package com.neoutils.finsight.feature.recurring.modal.recurringForm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.core.analytics.Analytics
import com.neoutils.finsight.feature.recurring.event.CreateRecurring
import com.neoutils.finsight.feature.recurring.event.EditRecurring
import com.neoutils.finsight.core.analytics.crashlytics.Crashlytics
import com.neoutils.finsight.feature.accounts.model.Account
import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.recurring.model.Recurring
import com.neoutils.finsight.feature.recurring.state.RecurringForm
import com.neoutils.finsight.feature.accounts.repository.IAccountRepository
import com.neoutils.finsight.feature.categories.repository.ICategoryRepository
import com.neoutils.finsight.feature.creditCards.repository.ICreditCardRepository
import com.neoutils.finsight.feature.recurring.usecase.SaveRecurringUseCase
import com.neoutils.finsight.core.ui.component.ModalManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RecurringFormViewModel(
    private val recurring: Recurring?,
    private val categoryRepository: ICategoryRepository,
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val saveRecurringUseCase: SaveRecurringUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val selectedAccount = MutableStateFlow<Account?>(null)
    private val selectedCreditCard = MutableStateFlow<CreditCard?>(null)

    init {
        viewModelScope.launch {
            selectedAccount.value = recurring?.accountId?.let { accountRepository.getAccountById(it) }
            selectedCreditCard.value = recurring?.creditCardId?.let { creditCardRepository.getCreditCardById(it) }
        }
    }

    private val categories = categoryRepository.observeAllCategories()
    private val accounts = accountRepository.observeAllAccounts()
    private val creditCards = creditCardRepository.observeAllCreditCards()

    val uiState = combine(
        selectedAccount,
        selectedCreditCard,
        categories,
        accounts,
        creditCards,
    ) { account, creditCard, cats, accs, cards ->
        RecurringFormUiState(
            accounts = accs,
            selectedAccount = account ?: accs.firstOrNull { it.isDefault },
            creditCards = cards,
            selectedCreditCard = creditCard,
            incomeCategories = cats.filter { it.type == Category.Type.INCOME },
            expenseCategories = cats.filter { it.type == Category.Type.EXPENSE },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RecurringFormUiState(
            selectedAccount = null,
            selectedCreditCard = null,
        ),
    )

    fun onAction(action: RecurringFormAction) {
        when (action) {
            is RecurringFormAction.SelectAccount -> {
                selectedAccount.value = action.account
            }

            is RecurringFormAction.SelectCreditCard -> {
                selectedCreditCard.value = action.creditCard
            }

            is RecurringFormAction.Submit -> {
                submit(action.form)
            }
        }
    }

    private fun submit(form: RecurringForm) =
        viewModelScope.launch {
            saveRecurringUseCase(
                id = recurring?.id ?: 0L,
                type = form.type,
                amount = form.amount,
                title = form.title.ifEmpty { null },
                dayOfMonth = form.dayOfMonth,
                category = form.category,
                account = form.account,
                creditCard = form.creditCard,
                createdAt = recurring?.createdAt,
                isActive = recurring?.isActive ?: true,
            ).onLeft {
                crashlytics.recordException(it)
            }.onRight {
                analytics.logEvent(
                    if (recurring != null) EditRecurring(form) else CreateRecurring(form)
                )
                modalManager.dismissAll()
            }
        }
}
