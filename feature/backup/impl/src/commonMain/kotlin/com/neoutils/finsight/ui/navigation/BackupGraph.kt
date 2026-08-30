package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.neoutils.finsight.feature.backup.api.BackupEntry
import com.neoutils.finsight.feature.backup.api.BackupGraph
import com.neoutils.finsight.feature.backup.api.BackupRoute
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.ui.component.AnimatedVisibilityScopeProvider
import com.neoutils.finsight.ui.screen.backup.BackupScreen
import com.neoutils.finsight.ui.screen.backupHistory.BackupHistoryRoute
import com.neoutils.finsight.ui.screen.backupHistory.BackupHistoryScreen

/**
 * Backup's destinations, registered wherever the caller is building. Nothing outside this
 * module calls it: settings hosts these screens and reaches them through [BackupEntry],
 * which is what the dependency rules leave as the sanctioned path.
 */
internal fun NavGraphBuilder.backupGraph() {
    navigation<BackupGraph>(
        startDestination = BackupRoute,
    ) {
        composable<BackupRoute> {
            val navController = LocalNavController.current

            AnimatedVisibilityScopeProvider {
                BackupScreen(
                    onNavigateBack = { navController.navigateUp() },
                    onNavigateToHistory = { navController.navigate(BackupHistoryRoute) },
                )
            }
        }

        // Internal to this feature, which is what the project's convention reserves the
        // `api` from: nobody outside backup navigates straight to a list of backup files
        // (design D15).
        composable<BackupHistoryRoute> {
            val navController = LocalNavController.current

            AnimatedVisibilityScopeProvider {
                BackupHistoryScreen(
                    onNavigateBack = { navController.navigateUp() },
                )
            }
        }
    }
}
