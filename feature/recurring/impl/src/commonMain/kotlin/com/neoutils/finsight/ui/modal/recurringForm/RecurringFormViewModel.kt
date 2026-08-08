package com.neoutils.finsight.ui.modal.recurringForm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.form.RecurringForm
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.CreateRecurring
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.analytics.event.EditRecurring
import com.neoutils.finsight.domain.extension.currencyOf
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.usecase.SaveRecurringUseCase
import com.neoutils.finsight.extension.combine
import com.neoutils.finsight.ui.component.ModalManager
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

    private val selectedAccount = MutableStateFlow(recurring?.account)
    private val selectedCreditCard = MutableStateFlow(recurring?.creditCard)

    /**
     * The selected card's currency, read off the `LIABILITY` account it projects onto
     * (design D17). Resolved beside the card so the two cannot disagree.
     */
    private val creditCardCurrency = selectedCreditCard.map { card ->
        card?.let { accountRepository.currencyOf(it) }
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
        creditCardCurrency,
    ) { account, creditCard, cats, accs, cards, cardCurrency ->
        RecurringFormUiState(
            accounts = accs,
            selectedAccount = account ?: accs.firstOrNull { it.isDefault },
            creditCards = cards,
            selectedCreditCard = creditCard,
            incomeCategories = cats.filter { it.type == Category.Type.INCOME },
            expenseCategories = cats.filter { it.type == Category.Type.EXPENSE },
            creditCardCurrency = cardCurrency,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RecurringFormUiState(
            selectedAccount = recurring?.account,
            selectedCreditCard = recurring?.creditCard,
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
                // Editing an archived recurring preserves the flag; dropping this
                // argument for the default would silently unarchive it.
                isArchived = recurring?.isArchived ?: false,
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
