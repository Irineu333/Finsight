package com.neoutils.finsight.feature.recurring.modal.viewRecurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.core.analytics.crashlytics.Crashlytics
import com.neoutils.finsight.feature.accounts.repository.IAccountRepository
import com.neoutils.finsight.feature.categories.repository.ICategoryRepository
import com.neoutils.finsight.feature.creditCards.repository.ICreditCardRepository
import com.neoutils.finsight.feature.recurring.error.RecurringError
import com.neoutils.finsight.feature.recurring.exception.RecurringException
import com.neoutils.finsight.feature.recurring.repository.IRecurringRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

class ViewRecurringViewModel(
    private val recurringId: Long,
    private val recurringRepository: IRecurringRepository,
    private val accountRepository: IAccountRepository,
    private val categoryRepository: ICategoryRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    val uiState = flow {
        val recurring = recurringRepository.getRecurringById(recurringId)

        if (recurring == null) {
            crashlytics.recordException(RecurringException(RecurringError.NOT_FOUND))
            emit(ViewRecurringUiState.Error)
            return@flow
        }

        val account = recurring.accountId?.let { accountRepository.getAccountById(it) }
        val category = recurring.categoryId?.let { categoryRepository.getCategoryById(it) }
        val creditCard = recurring.creditCardId?.let { creditCardRepository.getCreditCardById(it) }

        emit(
            ViewRecurringUiState.Content(
                recurring = recurring,
                account = account,
                category = category,
                creditCard = creditCard,
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ViewRecurringUiState.Loading,
    )
}
