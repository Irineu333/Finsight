package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.neoutils.finsight.feature.creditcards.api.CreditCardsRoute
import com.neoutils.finsight.feature.creditcards.api.InstallmentsRoute
import com.neoutils.finsight.feature.creditcards.api.InvoiceTransactionsRoute
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.ui.component.AnimatedVisibilityScopeProvider
import com.neoutils.finsight.ui.screen.creditCards.CreditCardsScreen
import com.neoutils.finsight.ui.screen.installments.InstallmentsScreen
import com.neoutils.finsight.ui.screen.invoiceTransactions.InvoiceTransactionsScreen

fun NavGraphBuilder.creditCardsGraph() {
    composable<CreditCardsRoute> { backStackEntry ->
        val navController = LocalNavController.current
        val route = backStackEntry.toRoute<CreditCardsRoute>()

        AnimatedVisibilityScopeProvider {
            CreditCardsScreen(
                initialCreditCardId = route.creditCardId,
                onNavigateBack = { navController.navigateUp() },
            )
        }
    }

    composable<InvoiceTransactionsRoute> { backStackEntry ->
        val navController = LocalNavController.current
        val route = backStackEntry.toRoute<InvoiceTransactionsRoute>()

        InvoiceTransactionsScreen(
            creditCardId = route.creditCardId,
            onNavigateBack = { navController.navigateUp() },
        )
    }

    composable<InstallmentsRoute> {
        val navController = LocalNavController.current

        InstallmentsScreen(
            onNavigateBack = { navController.navigateUp() },
        )
    }
}
