package com.neoutils.finsight.ui.navigation

import com.neoutils.finsight.feature.shell.api.NavDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The catalog is the single source of the shell's two projections: the bottom bar takes the
 * `primaryTab` destinations, the quick-actions grid takes the rest — both in catalog order.
 *
 * Order is the part worth a test. The E2E suite used to walk the grid with eight
 * `scrollUntilVisible` steps and a comment claiming they were "asserted in catalog order", which
 * they were not: `scrollUntilVisible` finds an element wherever it happens to be. Order is a
 * property of a list, so it is checked here, where checking it is exact and free.
 */
class AppNavCatalogTest {

    private val destinations = AppNavCatalog.destinations

    private val List<NavDestination>.names get() = map { it.name }

    @Test
    fun `bottom bar takes the first two, in this order`() {
        assertEquals(
            listOf("dashboard", "transactions"),
            destinations.filter { it.primaryTab }.names
        )
        assertEquals(
            listOf("dashboard", "transactions"),
            destinations.take(2).names,
            "primary tabs lead the catalog: the grid below is the remainder, not a filter of the whole"
        )
    }

    @Test
    fun `the grid is the rest of the catalog, in this order`() {
        assertEquals(
            listOf(
                "budgets",
                "categories",
                "creditCards",
                "accounts",
                "recurring",
                "report",
                "installments",
                "settings",
                "support",
            ),
            destinations.filterNot { it.primaryTab }.names
        )
    }

    @Test
    fun `support closes the catalog on every platform`() {
        assertEquals("support", destinations.last().name)
        assertTrue(
            destinations.none { it.mobileOnly },
            "nothing is excluded from the desktop rail today; a new mobileOnly destination has to " +
                "state itself here, because the rail silently drops it"
        )
    }

    @Test
    fun `a name is the route's, with no suffix and no restatement`() {
        assertEquals(
            listOf(
                "dashboard",
                "transactions",
                "budgets",
                "categories",
                "creditCards",
                "accounts",
                "recurring",
                "report",
                "installments",
                "settings",
                "support",
            ),
            destinations.names,
            "derived from the route class: `CreditCardsRoute` and `ReportGraph` both lose their suffix"
        )
        assertEquals(
            destinations.map { "nav_item_${it.name}" },
            destinations.map { it.testTag },
        )
    }

    @Test
    fun `no two destinations answer to the same tag`() {
        val duplicates = destinations.names
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }

        assertTrue(
            duplicates.isEmpty(),
            "two destinations sharing a name share `nav_item_<name>`, and every E2E flow that taps " +
                "it would be tapping an ambiguous selector: $duplicates"
        )
    }
}
