@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.viewCategory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.extension.accountType
import com.neoutils.finsight.extension.displaySign
import com.neoutils.finsight.extension.interceptAbsence
import com.neoutils.finsight.extension.toYearMonth
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plusMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ViewCategoryViewModel(
    categoryId: Long,
    categoryRepository: ICategoryRepository,
    private val entryRepository: IEntryRepository,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val _events = Channel<ViewCategoryEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val selectedYearMonth = MutableStateFlow(Clock.System.now().toYearMonth())

    val uiState = combine(
        categoryRepository.observeCategoryById(categoryId)
            .interceptAbsence(
                onMissing = { crashlytics.recordException(DetailNotFoundException("Category", categoryId)) },
                onDisappeared = { _events.send(ViewCategoryEvent.Dismiss) },
            ),
        selectedYearMonth,
    ) { category, yearMonth ->
        category ?: return@combine ViewCategoryUiState.Error
        // Σ entries of the category's chart account in the month, read from the ledger.
        // The natural balance is debit-positive; the ledger's own display convention
        // turns it into the positive figure a category is expected to read as.
        val displaySign = category.type.accountType.displaySign
        val totalAmount = entryRepository.balanceInMonth(yearMonth, category.accountId) * displaySign
        val transactionCount = entryRepository.entryCountInMonth(yearMonth, category.accountId)
        ViewCategoryUiState.Content(
            category = category,
            selectedYearMonth = yearMonth,
            totalAmount = totalAmount,
            transactionCount = transactionCount,
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
