package com.neoutils.finsight.feature.dashboard.screen

import com.neoutils.finsight.core.domain.model.Account

data class DashboardAccountUi(
    val account: Account,
    val balance: Double,
)
