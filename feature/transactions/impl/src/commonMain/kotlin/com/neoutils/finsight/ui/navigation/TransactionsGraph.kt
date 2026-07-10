package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.feature.transactions.api.TransactionTargetNavType
import com.neoutils.finsight.feature.transactions.api.TransactionTypeNavType
import com.neoutils.finsight.feature.transactions.api.TransactionsRoute
import com.neoutils.finsight.ui.component.AnimatedVisibilityScopeProvider
import com.neoutils.finsight.ui.screen.transactions.TransactionsScreen
import kotlin.reflect.typeOf

fun NavGraphBuilder.transactionsGraph() {
    composable<TransactionsRoute>(
        typeMap = mapOf(
            typeOf<Transaction.Type?>() to TransactionTypeNavType(),
            typeOf<Transaction.Target?>() to TransactionTargetNavType(),
        )
    ) { backStackEntry ->
        AnimatedVisibilityScopeProvider {
            val route = backStackEntry.toRoute<TransactionsRoute>()

            TransactionsScreen(
                categoryType = route.filterType,
                target = route.filterTarget,
            )
        }
    }
}
