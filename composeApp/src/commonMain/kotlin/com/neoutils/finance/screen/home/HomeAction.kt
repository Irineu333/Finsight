package com.neoutils.finance.screen.home

sealed class HomeAction {
    data class Navigate(
        val route: HomeRoute
    ) : HomeAction()

    data object OnBack : HomeAction()
}