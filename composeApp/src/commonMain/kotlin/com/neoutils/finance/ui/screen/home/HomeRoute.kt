package com.neoutils.finance.ui.screen.home

import kotlinx.serialization.Serializable

sealed class HomeRoute {
    @Serializable
    data object Dashboard : HomeRoute()

    @Serializable
    data object Transactions : HomeRoute()
}