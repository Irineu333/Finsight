package com.neoutils.finsight.feature.shell.api

import androidx.compose.ui.graphics.vector.ImageVector
import com.neoutils.finsight.navigation.NavRoute
import com.neoutils.finsight.ui.component.BottomNavigationItem
import org.jetbrains.compose.resources.StringResource

/**
 * Single source of truth for a navigable section of the app. The adaptive shell projects the
 * catalog into each affordance: the desktop rail (`!mobileOnly`), the mobile bottom bar
 * (`primaryTab`) and the mobile quick-actions grid (`!primaryTab`). The `mobileOnly` flag marks a
 * destination whose feature is not supported on desktop, excluding it from the desktop rail.
 */
data class NavDestination(
    override val icon: ImageVector,
    override val labelRes: StringResource,
    val route: NavRoute,
    val primaryTab: Boolean = false,
    val mobileOnly: Boolean = false,
) : BottomNavigationItem
