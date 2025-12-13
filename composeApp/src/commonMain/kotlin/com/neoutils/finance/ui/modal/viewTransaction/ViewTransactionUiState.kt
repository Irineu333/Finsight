package com.neoutils.finance.ui.modal.viewTransaction

import com.neoutils.finance.domain.model.Transaction

data class ViewTransactionUiState(
    val transaction: Transaction,
    val creditCardName: String? = null
) {
    val title = (transaction.title ?: transaction.category?.name) ?: "Sem título"
}