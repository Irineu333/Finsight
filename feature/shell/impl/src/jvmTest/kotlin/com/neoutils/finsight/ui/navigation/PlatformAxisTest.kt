package com.neoutils.finsight.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import com.neoutils.finsight.feature.dashboard.api.DashboardRoute
import com.neoutils.finsight.feature.shell.api.FeaturePlatform
import com.neoutils.finsight.feature.shell.api.NavDestination
import com.neoutils.finsight.isDesktop
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.nav_dashboard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The platform axis, on the platform it is hardest to get right: the desktop, where **both**
 * directions have an answer.
 *
 * It is a `jvmTest` on purpose. The whole subject is what `isDesktop` decides, so a test of it in
 * `commonTest` would assert one thing when run on the JVM and the opposite when run on Android —
 * which is the confusion this exists to rule out, not to reproduce.
 */
class PlatformAxisTest {

    private fun destination(onlyOn: FeaturePlatform?) = NavDestination(
        icon = Icons.Default.Dashboard,
        labelRes = Res.string.nav_dashboard,
        route = DashboardRoute,
        onlyOn = onlyOn,
    )

    @Test
    fun `the catalog tells the two directions apart`() {
        val mobile = destination(FeaturePlatform.MOBILE)
        val desktop = destination(FeaturePlatform.DESKTOP)
        val anywhere = destination(null)

        assertTrue(mobile.mobileOnly && !mobile.desktopOnly, "mobile-only read as something else")
        assertTrue(desktop.desktopOnly && !desktop.mobileOnly, "desktop-only read as something else")
        assertFalse(
            anywhere.mobileOnly || anywhere.desktopOnly,
            "a destination with no restriction was classified as restricted to one platform",
        )
    }

    @Test
    fun `on the desktop, a desktop-only feature is offered and a mobile-only one is not`() {
        assertTrue(isDesktop, "this test states what the desktop does, and is not running on it")

        assertTrue(
            destination(FeaturePlatform.DESKTOP).isOffered,
            "the desktop withheld a feature that exists only on the desktop",
        )
        assertFalse(
            destination(FeaturePlatform.MOBILE).isOffered,
            "the desktop offered a feature with no desktop implementation",
        )
        assertTrue(
            destination(null).isOffered,
            "a feature classified for no platform has to be offered on every one",
        )
    }

    /**
     * The orthogonality, stated where it can be: availability is a function of the platform alone.
     *
     * A narrow window on the desktop is still the desktop, and this holds because there is no width
     * to pass — [NavDestination.isOffered] takes no argument and reads only [FeaturePlatform], so no
     * caller can make the answer depend on how the window happens to be sized. The width decides
     * which affordance the shell draws, never whether a feature exists.
     */
    @Test
    fun `a narrow window on the desktop does not hide a desktop-only feature`() {
        val desktopOnly = destination(FeaturePlatform.DESKTOP)

        assertEquals(
            FeaturePlatform.DESKTOP.isCurrent,
            desktopOnly.isOffered,
            "availability stopped being the platform's answer alone",
        )
        assertEquals(
            isDesktop,
            FeaturePlatform.DESKTOP.isCurrent,
            "the platform axis reads something other than the platform",
        )
    }

    @Test
    fun `nothing in the catalog is hidden on the desktop today`() {
        val hidden = AppNavCatalog.destinations.filterNot { it.isOffered }

        assertTrue(
            hidden.isEmpty(),
            "the desktop rail silently drops these, and a destination that is meant to be hidden " +
                "has to say so here: ${hidden.map { it.name }}",
        )
    }
}
