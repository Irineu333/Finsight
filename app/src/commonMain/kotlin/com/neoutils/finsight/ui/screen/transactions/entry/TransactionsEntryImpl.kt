package com.neoutils.finsight.ui.screen.transactions.entry

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.ui.screen.home.HomeRoute
import com.neoutils.finsight.ui.screen.transactions.TransactionsEntry
import com.neoutils.finsight.ui.screen.transactions.TransactionsScreen
import com.neoutils.finsight.util.TransactionTargetNavType
import com.neoutils.finsight.util.TransactionTypeNavType
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
