@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal.viewCategory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.extension.toYearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plusMonth
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ViewCategoryViewModel(
    private val category: Category,
    private val categoryRepository: ICategoryRepository,
    private val repository: ITransactionRepository
) : ViewModel() {

    private val selectedYearMonth = MutableStateFlow(Clock.System.now().toYearMonth())

    val uiState = combine(
        categoryRepository.observeCategoryById(category.id).filterNotNull(),
        repository.getAllTransactions(),
        selectedYearMonth
    ) { category, transactions, yearMonth ->

        val transactionsForMonth = transactions.filter {
            it.categoryId == category.id && it.date.yearMonth == yearMonth
        }

        ViewCategoryUiState(
            category = category,
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
