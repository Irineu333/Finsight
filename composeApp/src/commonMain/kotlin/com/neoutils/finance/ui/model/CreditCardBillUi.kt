package com.neoutils.finance.ui.model

import androidx.compose.ui.graphics.Color

data class CreditCardBillUi(
    val bill: String,
    val limit: String,
    val availableLimit: String,
    val usagePercentage: Double,
    val showProgress: Boolean,
    val statusLabel: String = "",
    val statusColor: Color = Color.Unspecified
)

