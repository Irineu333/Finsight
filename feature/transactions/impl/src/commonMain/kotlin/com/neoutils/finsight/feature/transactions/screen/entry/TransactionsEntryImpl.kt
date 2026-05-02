package com.neoutils.finsight.feature.transactions.screen.entry

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.neoutils.finsight.feature.transactions.model.Transaction
import com.neoutils.finsight.feature.home.route.HomeRoute
import com.neoutils.finsight.feature.transactions.screen.TransactionsEntry
import com.neoutils.finsight.feature.transactions.screen.TransactionsScreen
import com.neoutils.finsight.feature.transactions.util.TransactionTargetNavType
import com.neoutils.finsight.feature.transactions.util.TransactionTypeNavType
import kotlin.reflect.typeOf

class TransactionsEntryImpl : TransactionsEntry() {

    override fun NavGraphBuilder.register(navController: NavController) {
        composable<HomeRoute.Transactions>(
            typeMap = mapOf(
                typeOf<Transaction.Type?>() to TransactionTypeNavType(),
                typeOf<Transaction.Target?>() to TransactionTargetNavType(),
            ),
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<HomeRoute.Transactions>()

            TransactionsScreen(
                categoryType = route.filterType,
                target = route.filterTarget,
            )
        }
    }
}
