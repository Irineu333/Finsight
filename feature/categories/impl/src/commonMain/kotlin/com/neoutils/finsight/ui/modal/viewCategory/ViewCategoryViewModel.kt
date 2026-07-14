@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.viewCategory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.extension.toYearMonth
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.withIndex
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plusMonth
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ViewCategoryViewModel(
    categoryId: Long,
    categoryRepository: ICategoryRepository,
    transactionRepository: ITransactionRepository,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val _events = Channel<ViewCategoryEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val selectedYearMonth = MutableStateFlow(Clock.System.now().toYearMonth())

    private val transactions = flow {
        emit(transactionRepository.getAllTransactions())
    }

    val uiState = combine(
        categoryRepository.observeCategoryById(categoryId)
            .distinctUntilChanged()
            .withIndex()
            .onEach { (index, category) ->
                when {
                    category != null -> Unit
                    index == 0 -> crashlytics.recordException(DetailNotFoundException("Category", categoryId))
                    else -> _events.send(ViewCategoryEvent.Dismiss)
                }
            }
            .filter { (index, category) -> category != null || index == 0 },
        transactions,
        selectedYearMonth,
    ) { (_, category), transactions, yearMonth ->
        category ?: return@combine ViewCategoryUiState.Error
        val transactionsForMonth = transactions.filter {
            it.category?.id == category.id && it.date.yearMonth == yearMonth
        }
        ViewCategoryUiState.Content(
            category = category,
            selectedYearMonth = yearMonth,
            totalAmount = transactionsForMonth.sumOf { it.amount },
            transactionCount = transactionsForMonth.size,
        )
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
