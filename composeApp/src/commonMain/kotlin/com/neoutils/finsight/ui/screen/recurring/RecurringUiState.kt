package com.neoutils.finsight.ui.screen.recurring

import com.neoutils.finsight.domain.model.Recurring

data class RecurringUiState(
    val recurring: List<Recurring> = emptyList(),
)
