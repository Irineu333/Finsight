package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.feature.transactions.api.TransactionTargetNavType
import com.neoutils.finsight.feature.transactions.api.TransactionTypeNavType
import com.neoutils.finsight.feature.transactions.api.TransactionsRoute
import com.neoutils.finsight.navigation.NavGraphRoute
import com.neoutils.finsight.ui.component.AnimatedVisibilityScopeProvider
import com.neoutils.finsight.ui.screen.transactions.TransactionsScreen
import kotlin.reflect.typeOf
import kotlinx.serialization.Serializable

@Serializable
data object TransactionsGraph : NavGraphRoute

fun NavGraphBuilder.transactionsGraph() {
    navigation<TransactionsGraph>(
        startDestination = TransactionsRoute(),
    ) {
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
}
