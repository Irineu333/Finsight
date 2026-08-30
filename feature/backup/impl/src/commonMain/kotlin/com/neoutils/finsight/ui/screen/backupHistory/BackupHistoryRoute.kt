package com.neoutils.finsight.ui.screen.backupHistory

import com.neoutils.finsight.navigation.NavRoute
import kotlinx.serialization.Serializable

/**
 * The copies the vault is keeping.
 *
 * It is declared here and not in the feature's `api`, which is what the project's
 * convention reserves for the routes that are *externally navigable* (design D15): the door
 * to this screen is the backup screen's own tile, and nothing outside this feature has a
 * reason to land on a list of backup files. `BackupGraph` stays the one node the rest of
 * the app knows.
 */
@Serializable
data object BackupHistoryRoute : NavRoute
