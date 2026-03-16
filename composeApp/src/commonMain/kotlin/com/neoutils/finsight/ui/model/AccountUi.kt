package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.Account

data class AccountUi(
    val account: Account,
    val initialBalance: Double,
    val balance: Double,
    val income: Double,
    val expense: Double,
    val adjustment: Double,
    val invoicePayment: Double,
    val advancePayment: Double,
)
