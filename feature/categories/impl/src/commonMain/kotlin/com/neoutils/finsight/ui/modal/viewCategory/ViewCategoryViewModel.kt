@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.viewCategory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.domain.model.CategoryRetirability
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.usecase.ResolveCategoryRetirabilityUseCase
import com.neoutils.finsight.domain.usecase.UnarchiveCategoryUseCase
import com.neoutils.finsight.ui.model.retireActionOf
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
import kotlinx.coroutines.launch
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plusMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ViewCategoryViewModel(
    categoryId: Long,
    categoryRepository: ICategoryRepository,
    private val entryRepository: IEntryRepository,
    private val resolveRetirability: ResolveCategoryRetirabilityUseCase,
    private val unarchiveCategory: UnarchiveCategoryUseCase,
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
        // Same reason as the accounts screen: the totals below are SQL aggregates,
        // so the ledger has to say when it moved.
        entryRepository.observeLedgerChanges(),
    ) { category, yearMonth, _ ->
        category ?: return@combine ViewCategoryUiState.Error
        // Σ entries carrying the category's dimension in the month, read from the
        // ledger. The natural balance is debit-positive; the ledger's own display
        // convention turns it into the positive figure a category reads as.
        val displaySign = category.type.accountType.displaySign
        val totalAmount = entryRepository.dimensionBalanceInMonth(yearMonth, category.dimensionId) * displaySign
        val transactionCount = entryRepository.dimensionEntryCountInMonth(yearMonth, category.dimensionId)
        // Whether deleting is refused (so the screen offers archiving instead) is one
        // rule with a single owner — the same one DeleteCategoryUseCase consumes.
        val retirability = resolveRetirability(category)
        ViewCategoryUiState.Content(
            category = category,
            retireAction = retireActionOf(retirability !is CategoryRetirability.Deletable),
            selectedYearMonth = yearMonth,
            totalAmount = totalAmount,
            transactionCount = transactionCount,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ViewCategoryUiState.Loading,
    )

    fun onAction(action: ViewCategoryAction) {
        when (action) {
            ViewCategoryAction.NextMonth ->
                selectedYearMonth.value = selectedYearMonth.value.plusMonth()

            ViewCategoryAction.PreviousMonth ->
                selectedYearMonth.value = selectedYearMonth.value.minusMonth()

            ViewCategoryAction.Unarchive -> unarchive()
        }
    }

    // Reversible and innocuous (design D1): no confirmation. The modal observes the
    // category, so flipping isArchived swaps the button back on its own.
    private fun unarchive() {
        val category = (uiState.value as? ViewCategoryUiState.Content)?.category ?: return
        viewModelScope.launch {
            unarchiveCategory(category).onLeft { crashlytics.recordException(it) }
        }
    }
}
