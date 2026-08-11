package com.neoutils.finsight.feature.shell.api

import androidx.compose.ui.graphics.vector.ImageVector
import com.neoutils.finsight.navigation.NavRoute
import com.neoutils.finsight.ui.component.BottomNavigationItem
import org.jetbrains.compose.resources.StringResource

/**
 * Single source of truth for a navigable section of the app. The adaptive shell projects the
 * catalog into each affordance: the navigation rail of a wide window (`!mobileOnly`), the bottom
 * bar of a narrow one (`primaryTab`) and the dashboard's quick-actions grid (`!primaryTab`), which
 * accompanies the bottom bar and steps aside when the rail takes over. The `mobileOnly` flag marks
 * a destination whose feature is not supported on desktop, excluding it from the rail.
 */
data class NavDestination(
    override val icon: ImageVector,
    override val labelRes: StringResource,
    val route: NavRoute,
    val primaryTab: Boolean = false,
    val mobileOnly: Boolean = false,
) : BottomNavigationItem {

    /**
     * The section's identity, derived from its route — `DashboardRoute` becomes `dashboard`. It is
     * what the analytics screen name and the navigation item's test tag are both built from, so
     * neither has to restate a name the route already carries.
     */
    val name: String
        get() = route::class.simpleName
            .orEmpty()
            .removeSuffix("Route")
            .removeSuffix("Graph")
            .replaceFirstChar { it.lowercase() }

    override val testTag: String
        get() = "nav_item_$name"
}
