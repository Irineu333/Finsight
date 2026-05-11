package com.neoutils.finsight.feature.recurring.modal.recurringForm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.core.analytics.Analytics
import com.neoutils.finsight.core.analytics.crashlytics.Crashlytics
import com.neoutils.finsight.core.ui.component.ModalManager
import com.neoutils.finsight.core.ui.extension.CurrencyFormatter
import com.neoutils.finsight.feature.accounts.repository.IAccountRepository
import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.feature.categories.repository.ICategoryRepository
import com.neoutils.finsight.feature.creditCards.repository.ICreditCardRepository
import com.neoutils.finsight.feature.recurring.error.RecurringError
import com.neoutils.finsight.feature.recurring.event.CreateRecurring
import com.neoutils.finsight.feature.recurring.event.EditRecurring
import com.neoutils.finsight.feature.recurring.exception.RecurringException
import com.neoutils.finsight.feature.recurring.extension.isAccept
import com.neoutils.finsight.feature.recurring.model.form.RecurringForm
import com.neoutils.finsight.feature.recurring.repository.IRecurringRepository
import com.neoutils.finsight.feature.recurring.usecase.SaveRecurringUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RecurringFormViewModel(
    private val recurringId: Long?,
    private val recurringRepository: IRecurringRepository,
    private val categoryRepository: ICategoryRepository,
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val saveRecurringUseCase: SaveRecurringUseCase,
    private val currencyFormatter: CurrencyFormatter,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val isEditMode = recurringId != null

    private val recurring = flow {
        val recurring = recurringId?.let { recurringRepository.getRecurringById(it) }
        if (isEditMode && recurring == null) {
            crashlytics.recordException(RecurringException(RecurringError.NOT_FOUND))
        }
        emit(recurring)
    }

    private val form = MutableStateFlow<RecurringForm?>(null)

    private val categories = categoryRepository.observeAllCategories()
    private val accounts = accountRepository.observeAllAccounts()
    private val creditCards = creditCardRepository.observeAllCreditCards()

    init {
        setup()
    }

    private fun setup() = viewModelScope.launch {
        if (recurringId == null) {
            form.value = RecurringForm(account = accountRepository.getDefaultAccount())
            return@launch
        }

        val recurring = recurringRepository.getRecurringById(recurringId) ?: return@launch

        coroutineScope {
            val account = recurring.accountId?.let { id -> async { accountRepository.getAccountById(id) } }
            val creditCard = recurring.creditCardId?.let { id -> async { creditCardRepository.getCreditCardById(id) } }
            val category = recurring.categoryId?.let { id -> async { categoryRepository.getCategoryById(id) } }

            form.value = RecurringForm(
                id = recurring.id,
                type = recurring.type,
                amount = currencyFormatter.format(recurring.amount),
                title = recurring.title.orEmpty(),
                dayOfMonth = recurring.dayOfMonth.toString(),
                account = account?.await(),
                creditCard = creditCard?.await(),
                category = category?.await(),
                createdAt = recurring.createdAt,
                isActive = recurring.isActive,
            )
        }
    }

    val uiState = combine(
        form,
        recurring,
        categories,
        accounts,
        creditCards,
    ) { form, recurring, cats, accs, cards ->
        when {
            isEditMode && recurring == null -> RecurringFormUiState.Error
            form == null -> RecurringFormUiState.Loading
            else -> RecurringFormUiState.Content(
                form = form,
                accounts = accs,
                creditCards = cards,
                incomeCategories = cats.filter { it.type == Category.Type.INCOME },
                expenseCategories = cats.filter { it.type == Category.Type.EXPENSE },
                isEditMode = isEditMode,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecurringFormUiState.Loading,
    )

    fun onAction(action: RecurringFormAction) {
        when (action) {
            is RecurringFormAction.TypeChanged -> form.update {
                it?.copy(
                    type = action.type,
                    category = it.category?.takeIf { c ->
                        c.type.isAccept(action.type)
                    },
                )
            }

            is RecurringFormAction.AmountChanged -> {
                form.update { it?.copy(amount = action.amount) }
            }
            is RecurringFormAction.TitleChanged -> {
                form.update { it?.copy(title = action.title) }
            }
            is RecurringFormAction.DayOfMonthChanged -> {
                form.update { it?.copy(dayOfMonth = action.dayOfMonth) }
            }

            is RecurringFormAction.SelectAccount -> {
                form.update { it?.copy(account = action.account) }
            }
            is RecurringFormAction.SelectCreditCard -> {
                form.update { it?.copy(creditCard = action.creditCard) }
            }
            is RecurringFormAction.SelectCategory -> {
                form.update { it?.copy(category = action.category) }
            }

            RecurringFormAction.Submit -> submit()
        }
    }

    private fun submit() = viewModelScope.launch {
        val current = form.value ?: return@launch

        saveRecurringUseCase(
            id = current.id,
            type = current.type,
            amount = current.amount,
            title = current.title.ifEmpty { null },
            dayOfMonth = current.dayOfMonth,
            category = current.category,
            account = current.account,
            creditCard = current.creditCard,
            createdAt = current.createdAt,
            isActive = current.isActive,
        ).onLeft {
            crashlytics.recordException(it)
        }.onRight {
            analytics.logEvent(
                if (isEditMode) {
                    EditRecurring(current)
                } else {
                    CreateRecurring(current)
                }
            )
            modalManager.dismissAll()
        }
    }
}
