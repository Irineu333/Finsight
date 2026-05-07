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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

        coroutineScope {
            val account = recurring.accountId?.let { id -> async { accountRepository.getAccountById(id) } }
            val category = recurring.categoryId?.let { id -> async { categoryRepository.getCategoryById(id) } }
            val creditCard = recurring.creditCardId?.let { id -> async { creditCardRepository.getCreditCardById(id) } }

            emit(
                ViewRecurringUiState.Content(
                    recurring = recurring,
                    account = account?.await(),
                    category = category?.await(),
                    creditCard = creditCard?.await(),
                )
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ViewRecurringUiState.Loading,
    )
}
