@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.viewCategory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.domain.model.CategoryRetirability
import com.neoutils.finsight.domain.model.CurrencyBalance
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.usecase.ConsolidateFigureUseCase
import com.neoutils.finsight.domain.usecase.consolidationDateOf
import com.neoutils.finsight.domain.usecase.ResolveCategoryRetirabilityUseCase
import com.neoutils.finsight.domain.usecase.UnarchiveCategoryUseCase
import com.neoutils.finsight.ui.model.retireActionOf
import com.neoutils.finsight.extension.DisplayAmount
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plusMonth
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ViewCategoryViewModel(
    categoryId: Long,
    categoryRepository: ICategoryRepository,
    private val entryRepository: IEntryRepository,
    private val consolidateFigure: ConsolidateFigureUseCase,
    baseCurrencyRepository: IBaseCurrencyRepository,
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
        // The base is a flow and not a field read inside: a figure is not consolidable
        // without someone saying what it is read in, so an emission that forgot to follow
        // the preference has nothing to pass and fails to compile.
        baseCurrencyRepository.observe(),
    ) { category, yearMonth, _, base ->
        category ?: return@combine ViewCategoryUiState.Error
        // Σ entries carrying the category's dimension in the month, read from the
        // ledger. The natural balance is debit-positive; the ledger's own display
        // convention turns it into the positive figure a category reads as.
        val displaySign = category.type.accountType.displaySign
        // Per currency, because a category's entries are not bound to one account — the
        // consolidation layer is what reduces them, and the display sign is applied to each
        // currency before it, which is presentation of a number rather than arithmetic.
        val natural = entryRepository.dimensionBalanceInMonth(yearMonth, category.dimensionId)
        val totalAmount = consolidateFigure(
            balance = CurrencyBalance.of(natural.entries.mapValues { (_, amount) -> amount * displaySign }),
            base = base,
            date = consolidationDateOf(yearMonth, Clock.System.todayIn(TimeZone.currentSystemDefault())),
            policy = DisplayAmount.SignPolicy.NATURAL,
        ).figure
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
