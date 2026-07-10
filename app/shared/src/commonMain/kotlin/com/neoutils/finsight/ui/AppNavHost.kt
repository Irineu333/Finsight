package com.neoutils.finsight.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.neoutils.finsight.feature.home.api.HomeGraph
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.ui.navigation.accountsGraph
import com.neoutils.finsight.ui.navigation.budgetsGraph
import com.neoutils.finsight.ui.navigation.categoriesGraph
import com.neoutils.finsight.ui.navigation.creditCardsGraph
import com.neoutils.finsight.ui.navigation.homeGraph
import com.neoutils.finsight.ui.navigation.recurringGraph
import com.neoutils.finsight.ui.navigation.reportGraph
import com.neoutils.finsight.ui.navigation.supportGraph

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = LocalNavController.current,
        startDestination = HomeGraph,
        modifier = modifier,
    ) {
        homeGraph()

        categoriesGraph()

        creditCardsGraph()

        accountsGraph()

        budgetsGraph()

        recurringGraph()

        supportGraph()

        reportGraph()
    }
}
