package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.neoutils.finsight.feature.categories.api.CategoriesRoute
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.ui.screen.categories.CategoriesScreen
import kotlinx.serialization.Serializable

@Serializable
data object CategoriesGraph

fun NavGraphBuilder.categoriesGraph() {
    navigation<CategoriesGraph>(
        startDestination = CategoriesRoute,
    ) {
        composable<CategoriesRoute> {
            val navController = LocalNavController.current

            CategoriesScreen(
                onNavigateBack = { navController.navigateUp() },
            )
        }
    }
}
