package com.neoutils.finsight.ui.screen.transactions

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder

abstract class TransactionsEntry {

    abstract fun NavGraphBuilder.register(navController: NavController)
}
