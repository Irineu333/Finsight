package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.neoutils.finsight.feature.categories.api.CategoriesRoute
import com.neoutils.finsight.ui.screen.categories.CategoriesScreen

fun NavGraphBuilder.categoriesGraph(navController: NavController) {
    composable<CategoriesRoute> {
        CategoriesScreen(
            onNavigateBack = { navController.navigateUp() },
        )
    }
}
