package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.neoutils.finsight.feature.backup.api.BackupGraph
import com.neoutils.finsight.feature.backup.api.BackupRoute
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.ui.component.AnimatedVisibilityScopeProvider
import com.neoutils.finsight.ui.screen.backup.BackupScreen

fun NavGraphBuilder.backupGraph() {
    navigation<BackupGraph>(
        startDestination = BackupRoute,
    ) {
        composable<BackupRoute> {
            val navController = LocalNavController.current

            AnimatedVisibilityScopeProvider {
                BackupScreen(
                    onNavigateBack = { navController.navigateUp() },
                )
            }
        }
    }
}
