package com.neoutils.finsight.feature.dashboard.state

import com.neoutils.finsight.feature.accounts.model.Account

data class DashboardAccountUi(
    val account: Account,
    val balance: Double,
)