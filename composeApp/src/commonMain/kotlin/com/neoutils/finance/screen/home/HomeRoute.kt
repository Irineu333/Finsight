package com.neoutils.finance.screen.home

import kotlinx.serialization.Serializable

sealed class HomeRoute {
    @Serializable
    data object Dashboard : HomeRoute()

    @Serializable
    data object Transactions : HomeRoute()
}