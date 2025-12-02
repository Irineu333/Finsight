@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal.viewCategory

import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.extension.toYearMonth
import kotlinx.datetime.YearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class ViewCategoryUiState(
    val category: Category,
    val selectedYearMonth: YearMonth = Clock.System.now().toYearMonth(),
    val totalAmount: Double = 0.0,
    val transactionCount: Int = 0
)