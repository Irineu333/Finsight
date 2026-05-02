package com.neoutils.finsight.feature.transactions.screen

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder

abstract class TransactionsEntry {

    abstract fun NavGraphBuilder.register(navController: NavController)
}
