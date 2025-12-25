package com.neoutils.finance.ui.modal.editCreditCardLimit

import com.neoutils.finance.ui.model.InvoiceUi

data class EditCreditCardLimitUiState(
    val limit: Double = 0.0,
    val invoiceUi: InvoiceUi? = null,
)