package com.neoutils.finance.ui.modal.editTransaction

import com.neoutils.finance.domain.model.Category

data class EditTransactionUiState(
    val incomeCategories: List<Category> = emptyList(),
    val expenseCategories: List<Category> = emptyList(),
)