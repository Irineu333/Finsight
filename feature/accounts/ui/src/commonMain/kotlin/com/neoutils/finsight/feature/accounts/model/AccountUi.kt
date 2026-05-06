package com.neoutils.finsight.feature.accounts.model

import com.neoutils.finsight.feature.accounts.model.Account

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