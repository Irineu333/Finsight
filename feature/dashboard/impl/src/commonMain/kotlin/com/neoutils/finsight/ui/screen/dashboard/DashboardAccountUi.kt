package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.Account

data class DashboardAccountUi(
    val account: Account,
    val balance: Double,
)
