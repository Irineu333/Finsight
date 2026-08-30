package com.neoutils.finsight.feature.backup.api

import com.neoutils.finsight.navigation.NavGraphRoute
import com.neoutils.finsight.navigation.NavRoute
import kotlinx.serialization.Serializable

/**
 * The node the backup destinations hang from, and the stable target of a `popUpTo`.
 *
 * It is nested inside settings' subgraph — [BackupEntry] is what puts it there — because
 * backup is a door inside settings and not a section of its own: hanging from that node is
 * what keeps the navigation chrome showing settings as the current destination while any
 * backup screen is up.
 */
@Serializable
data object BackupGraph : NavGraphRoute

/**
 * The backup screen: export the archive to a file, and restore it from one.
 *
 * Externally navigable because the door is elsewhere — settings names this route, since
 * backup is not a tab and holds no place of its own in the navigation catalog.
 */
@Serializable
data object BackupRoute : NavRoute
