package com.neoutils.finsight.feature.dashboard.api

import androidx.navigation.NavGraphBuilder

interface DashboardEntry {
    context(builder: NavGraphBuilder)
    fun register()
}
