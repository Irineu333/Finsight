package com.neoutils.finance.ui.modal.addTransaction

import com.neoutils.finance.domain.model.Category

data class AddTransactionUiState(
    val incomeCategories: List<Category> = emptyList(),
    val expenseCategories: List<Category> = emptyList(),
)