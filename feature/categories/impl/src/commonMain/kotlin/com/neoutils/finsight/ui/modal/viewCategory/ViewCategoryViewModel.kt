package com.neoutils.finsight.ui.modal.viewCategory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.UnarchiveCategory
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.domain.model.CategoryRetirability
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.usecase.CalculateCategoryOverviewUseCase
import com.neoutils.finsight.domain.usecase.ObserveConsolidationChangesUseCase
import com.neoutils.finsight.domain.usecase.ResolveCategoryRetirabilityUseCase
import com.neoutils.finsight.domain.usecase.UnarchiveCategoryUseCase
import com.neoutils.finsight.ui.model.retireActionOf
import com.neoutils.finsight.extension.interceptAbsence
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ViewCategoryViewModel(
    categoryId: Long,
    categoryRepository: ICategoryRepository,
    private val calculateOverview: CalculateCategoryOverviewUseCase,
    private val resolveRetirability: ResolveCategoryRetirabilityUseCase,
    private val unarchiveCategory: UnarchiveCategoryUseCase,
    private val observeConsolidationChanges: ObserveConsolidationChangesUseCase,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val _events = Channel<ViewCategoryEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState = combine(
        categoryRepository.observeCategoryById(categoryId)
            .interceptAbsence(
                onMissing = { crashlytics.recordException(DetailNotFoundException("Category", categoryId)) },
                onDisappeared = { _events.send(ViewCategoryEvent.Dismiss) },
            ),
        // Same reason as the accounts screen: the figures below are SQL aggregates, so
        // the ledger has to say when it moved — and they are consolidated, so a rate
        // registered in settings has to say so too. Neither writes an entry.
        observeConsolidationChanges(),
    ) { category, _ ->
        category ?: return@combine ViewCategoryUiState.Error
        // Whether deleting is refused (so the screen offers archiving instead) is one
        // rule with a single owner — the same one DeleteCategoryUseCase consumes.
        val retirability = resolveRetirability(category)
        ViewCategoryUiState.Content(
            category = category,
            retireAction = retireActionOf(retirability !is CategoryRetirability.Deletable),
            // The window, the average and the variation are decided there and observed
            // here: this view model maps, and chooses nothing.
            overview = calculateOverview(category),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ViewCategoryUiState.Loading,
    )

    fun onAction(action: ViewCategoryAction) {
        when (action) {
            ViewCategoryAction.Unarchive -> unarchive()
        }
    }

    // Reversible and innocuous (design D1): no confirmation. The modal observes the
    // category, so flipping isArchived swaps the button back on its own.
    private fun unarchive() {
        val category = (uiState.value as? ViewCategoryUiState.Content)?.category ?: return
        viewModelScope.launch {
            unarchiveCategory(category)
                .onRight { analytics.logEvent(UnarchiveCategory(category)) }
                .onLeft { crashlytics.recordException(it) }
        }
    }
}
