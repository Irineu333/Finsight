package com.neoutils.finance.ui.model

data class CreditCardBillUi(
    val bill: String,
    val limit: String,
    val availableLimit: String,
    val usagePercentage: Double,
    val showProgress: Boolean
)
