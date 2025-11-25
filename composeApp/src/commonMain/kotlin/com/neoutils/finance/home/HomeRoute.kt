package com.neoutils.finance.home

import kotlinx.serialization.Serializable

sealed class HomeRoute {
    @Serializable
    data object Dashboard : HomeRoute()

    @Serializable
    data object Transactions : HomeRoute()

    @Serializable
    data object Budgets : HomeRoute()

    @Serializable
    data object Settings : HomeRoute()
}