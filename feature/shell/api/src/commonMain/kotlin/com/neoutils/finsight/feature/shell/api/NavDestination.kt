package com.neoutils.finsight.feature.shell.api

import androidx.compose.ui.graphics.vector.ImageVector
import com.neoutils.finsight.navigation.NavRoute
import com.neoutils.finsight.ui.component.BottomNavigationItem
import org.jetbrains.compose.resources.StringResource

/**
 * Single source of truth for a navigable section of the app. The adaptive shell projects the
 * catalog into each affordance: the navigation rail of a wide window (`isOffered`), the bottom
 * bar of a narrow one (`primaryTab`) and the dashboard's quick-actions grid (`!primaryTab`), which
 * accompanies the bottom bar and steps aside when the rail takes over.
 *
 * [onlyOn] is the platform axis, and it points both ways: a destination whose feature has no
 * desktop backing and one whose feature exists only there are stated with the same property, and
 * [isOffered] answers for both. It is orthogonal to the window's width — a narrow window on the
 * desktop is still the desktop.
 */
data class NavDestination(
    override val icon: ImageVector,
    override val labelRes: StringResource,
    val route: NavRoute,
    val primaryTab: Boolean = false,
    /** The platform this destination's feature is restricted to, or `null` when it runs anywhere. */
    val onlyOn: FeaturePlatform? = null,
) : BottomNavigationItem {

    /** Its feature has no desktop implementation, so the desktop never offers it. */
    val mobileOnly: Boolean get() = onlyOn == FeaturePlatform.MOBILE

    /** Its feature exists only on the desktop, so no mobile platform offers it. */
    val desktopOnly: Boolean get() = onlyOn == FeaturePlatform.DESKTOP

    /**
     * Whether this platform offers the destination at all — the one question every affordance asks
     * before drawing an entry point, so the answer cannot differ between them.
     */
    val isOffered: Boolean get() = onlyOn?.isCurrent != false

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
