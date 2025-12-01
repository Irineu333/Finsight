@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.data.Category
import com.neoutils.finance.data.CategoryRepository
import com.neoutils.finance.data.TransactionRepository
import com.neoutils.finance.extension.toYearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plusMonth
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ViewCategoryViewModel(
    private val category: Category,
    private val categoryRepository: CategoryRepository,
    private val repository: TransactionRepository
) : ViewModel() {

    private val selectedYearMonth = MutableStateFlow(Clock.System.now().toYearMonth())

    val uiState: StateFlow<ViewCategoryUiState> = combine(
        categoryRepository.observeCategoryById(category.id),
        repository.getAllTransactions(),
        selectedYearMonth
    ) { observedCategory, transactions, yearMonth ->
        val currentCategory = observedCategory ?: category
        val transactionsForMonth = transactions.filter {
            it.categoryId == currentCategory.id && it.date.yearMonth == yearMonth
        }

        ViewCategoryUiState(
            category = currentCategory,
            selectedYearMonth = yearMonth,
            totalAmount = transactionsForMonth.sumOf { it.amount },
            transactionCount = transactionsForMonth.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ViewCategoryUiState(category = category)
    )

    fun onAction(action: ViewCategoryAction) {
        when (action) {
            ViewCategoryAction.NextMonth -> {
                selectedYearMonth.value = selectedYearMonth.value.plusMonth()
            }
            ViewCategoryAction.PreviousMonth -> {
                selectedYearMonth.value = selectedYearMonth.value.minusMonth()
            }
        }
    }
}

data class ViewCategoryUiState(
    val category: Category,
    val selectedYearMonth: YearMonth = Clock.System.now().toYearMonth(),
    val totalAmount: Double = 0.0,
    val transactionCount: Int = 0
)

sealed class ViewCategoryAction {
    data object NextMonth : ViewCategoryAction()
    data object PreviousMonth : ViewCategoryAction()
}
