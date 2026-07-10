package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.neoutils.finsight.feature.categories.api.CategoriesRoute
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.ui.screen.categories.CategoriesScreen

fun NavGraphBuilder.categoriesGraph() {
    composable<CategoriesRoute> {
        val navController = LocalNavController.current

        CategoriesScreen(
            onNavigateBack = { navController.navigateUp() },
        )
    }
}
