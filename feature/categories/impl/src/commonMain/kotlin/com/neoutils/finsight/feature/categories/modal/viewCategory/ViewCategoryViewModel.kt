@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.feature.categories.modal.viewCategory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.core.analytics.crashlytics.Crashlytics
import com.neoutils.finsight.feature.categories.error.CategoryError
import com.neoutils.finsight.feature.categories.exception.CategoryException
import com.neoutils.finsight.feature.categories.repository.ICategoryRepository
import com.neoutils.finsight.feature.categories.usecase.CalculateCategoryAmountUseCase
import com.neoutils.finsight.core.utils.extension.toYearMonth
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plusMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ViewCategoryViewModel(
    private val categoryId: Long,
    categoryRepository: ICategoryRepository,
    private val calculateCategoryAmount: CalculateCategoryAmountUseCase,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val selectedYearMonth = MutableStateFlow(Clock.System.now().toYearMonth())

    private val category = viewModelScope.async { categoryRepository.getCategoryById(categoryId) }

    val uiState = selectedYearMonth.map { yearMonth ->
        coroutineScope {
            val amount = async { calculateCategoryAmount(categoryId, yearMonth) }
            val category = category.await()

            if (category == null) {
                crashlytics.recordException(CategoryException(CategoryError.NOT_FOUND))
                return@coroutineScope ViewCategoryUiState.Error
            }

            val resolvedAmount = amount.await()

            ViewCategoryUiState.Content(
                category = category,
                selectedYearMonth = yearMonth,
                totalAmount = resolvedAmount.totalAmount,
                transactionCount = resolvedAmount.transactionCount,
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
