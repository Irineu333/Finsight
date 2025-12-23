package com.neoutils.finance.domain.errors

data class AddTransactionErrors(
    val creditCardRequired: String = "Credit card is required for credit card transactions",
    val invoiceNotFound: String = "No unpaid invoice found for this credit card",
    val invoiceNotOpen: String = "Cannot add transaction to a closed invoice"
)

