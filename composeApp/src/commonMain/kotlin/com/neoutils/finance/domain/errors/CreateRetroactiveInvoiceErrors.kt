package com.neoutils.finance.domain.errors

data class CreateRetroactiveInvoiceErrors(
    val invoiceAlreadyExists: String = "Já existe uma fatura para este mês"
)