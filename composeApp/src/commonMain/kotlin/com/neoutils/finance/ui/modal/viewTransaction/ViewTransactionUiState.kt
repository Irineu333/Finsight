package com.neoutils.finance.ui.modal.viewTransaction

import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Transaction

data class ViewTransactionUiState(
    val transaction: Transaction,
    val category: Category? = null
) {
    val title = (transaction.title ?: category?.name) ?: "Sem título"
}