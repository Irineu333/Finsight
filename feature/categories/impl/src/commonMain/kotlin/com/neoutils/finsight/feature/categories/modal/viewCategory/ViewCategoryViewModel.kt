@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.feature.categories.modal.viewCategory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.core.analytics.crashlytics.Crashlytics
import com.neoutils.finsight.feature.categories.error.CategoryError
import com.neoutils.finsight.feature.categories.exception.CategoryException
import com.neoutils.finsight.feature.categories.repository.ICategoryRepository
import com.neoutils.finsight.feature.transactions.repository.ITransactionRepository
import com.neoutils.finsight.core.utils.extension.toYearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plusMonth
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ViewCategoryViewModel(
    private val categoryId: Long,
    private val categoryRepository: ICategoryRepository,
    private val transactionRepository: ITransactionRepository,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val selectedYearMonth = MutableStateFlow(Clock.System.now().toYearMonth())

    private val categoryFlow = flow {
        val category = categoryRepository.getCategoryById(categoryId)
        if (category == null) {
            crashlytics.recordException(CategoryException(CategoryError.NOT_FOUND))
        }
        emit(category)
    }

    private val transactions = flow {
        emit(transactionRepository.getAllTransactions())
    }

    val uiState = combine(
        categoryFlow,
        transactions,
        selectedYearMonth,
    ) { category, transactions, yearMonth ->
        if (category == null) {
            ViewCategoryUiState.Error
        } else {
            val transactionsForMonth = transactions.filter {
                it.categoryId == category.id && it.date.yearMonth == yearMonth
            }

            ViewCategoryUiState.Content(
                category = category,
                selectedYearMonth = yearMonth,
                totalAmount = transactionsForMonth.sumOf { it.amount },
                transactionCount = transactionsForMonth.size,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ViewCategoryUiState.Loading,
    )

    fun onAction(action: ViewCategoryAction) = when (action) {
        ViewCategoryAction.NextMonth -> {
            selectedYearMonth.value = selectedYearMonth.value.plusMonth()
        }
        ViewCategoryAction.PreviousMonth -> {
            selectedYearMonth.value = selectedYearMonth.value.minusMonth()
        }
    }
}
