package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.extension.ConsolidatedAmount
import com.neoutils.finsight.extension.DisplayAmount
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every way a component can be dropped, and where it lands.
 *
 * The gesture that produces these pairs of keys is the E2E suite's
 * (`dashboard/customization`), which proves once that a drag reaches this arithmetic at all. The
 * arithmetic itself is combinatorial — five branches, three sentinel keys, a boundary that is only
 * a count — and belongs here, where a case costs milliseconds instead of a minute of emulator.
 */
class DashboardEditLayoutTest {

    /**
     * A figure is what a component carries, not what this test is about: the amounts
     * below only have to differ from one another so the items do.
     */
    private fun figure(value: Double) = ConsolidatedAmount(
        terms = listOf(DisplayAmount.natural(value, "BRL", isApproximate = false)),
        isApproximate = false,
    )

    // Four components, built straight from their variants rather than from
    // `DashboardPreviewFactory` — the factory reads string resources, which a JVM test has no
    // access to, and an item's key comes from its variant either way. Which four is immaterial:
    // this is arithmetic over positions.
    private val total = DashboardEditItem(
        preview = DashboardComponentVariant.TotalBalance.Preview(
            component = DashboardComponent.TotalBalance(amount = figure(1.0)),
        ),
    )

    private val overall = DashboardEditItem(
        preview = DashboardComponentVariant.OverallBalanceStats.Preview(
            component = DashboardComponent.OverallBalanceStats(income = figure(2.0), expense = figure(3.0)),
        ),
    )

    private val concrete = DashboardEditItem(
        preview = DashboardComponentVariant.ConcreteBalanceStats.Preview(
            component = DashboardComponent.ConcreteBalanceStats(income = figure(4.0), expense = figure(5.0)),
        ),
    )

    private val pending = DashboardEditItem(
        preview = DashboardComponentVariant.PendingBalanceStats.Preview(
            component = DashboardComponent.PendingBalanceStats(pendingIncome = figure(6.0), pendingExpense = figure(7.0)),
        ),
    )

    private fun layout(
        active: List<DashboardEditItem>,
        available: List<DashboardEditItem>,
    ) = DashboardEditLayout(activeItems = active, availableItems = available)

    private val DashboardEditLayout.activeKeys get() = activeItems.map { it.key }
    private val DashboardEditLayout.availableKeys get() = availableItems.map { it.key }

    // --- Within one side: order changes, the boundary does not ------------------------------------

    @Test
    fun `dropping an active component on another reorders the active list`() {
        val moved = layout(
            active = listOf(total, overall, concrete),
            available = listOf(pending),
        ).move(fromKey = total.key, toKey = concrete.key)

        assertEquals(listOf(overall.key, concrete.key, total.key), moved.activeKeys)
        assertEquals(listOf(pending.key), moved.availableKeys)
    }

    @Test
    fun `dropping an available component on another reorders the available list`() {
        val moved = layout(
            active = listOf(total),
            available = listOf(overall, concrete, pending),
        ).move(fromKey = pending.key, toKey = overall.key)

        assertEquals(listOf(total.key), moved.activeKeys)
        assertEquals(listOf(pending.key, overall.key, concrete.key), moved.availableKeys)
    }

    // --- Across the boundary, by landing on a component ------------------------------------------

    @Test
    fun `an active component dropped on an available one leaves the dashboard`() {
        val moved = layout(
            active = listOf(total, overall),
            available = listOf(concrete, pending),
        ).move(fromKey = overall.key, toKey = pending.key)

        assertEquals(listOf(total.key), moved.activeKeys)
        assertEquals(listOf(concrete.key, pending.key, overall.key), moved.availableKeys)
    }

    @Test
    fun `an available component dropped on an active one joins the dashboard`() {
        val moved = layout(
            active = listOf(total, overall),
            available = listOf(concrete, pending),
        ).move(fromKey = pending.key, toKey = total.key)

        assertEquals(listOf(pending.key, total.key, overall.key), moved.activeKeys)
        assertEquals(listOf(concrete.key), moved.availableKeys)
    }

    // --- Across the boundary, by landing on the boundary itself -----------------------------------
    // The header and the empty-available slot are the same drop, from either side.

    @Test
    fun `an active component dropped on the header lands last among the active ones`() {
        val moved = layout(
            active = listOf(total, overall, concrete),
            available = listOf(pending),
        ).move(fromKey = total.key, toKey = EDIT_SECTION_HEADER_KEY)

        assertEquals(listOf(overall.key, concrete.key), moved.activeKeys)
        assertEquals(listOf(total.key, pending.key), moved.availableKeys)
    }

    @Test
    fun `an available component dropped on the header lands last among the active ones`() {
        val moved = layout(
            active = listOf(total, overall),
            available = listOf(concrete, pending),
        ).move(fromKey = pending.key, toKey = EDIT_SECTION_HEADER_KEY)

        assertEquals(listOf(total.key, overall.key, pending.key), moved.activeKeys)
        assertEquals(listOf(concrete.key), moved.availableKeys)
    }

    @Test
    fun `the empty available slot is the header by another name`() {
        val moved = layout(
            active = listOf(total, overall),
            available = emptyList(),
        ).move(fromKey = total.key, toKey = EDIT_AVAILABLE_PLACEHOLDER_KEY)

        assertEquals(listOf(overall.key), moved.activeKeys)
        assertEquals(listOf(total.key), moved.availableKeys)
    }

    // --- The empty-active slot --------------------------------------------------------------------

    @Test
    fun `dropping on the empty active slot makes the component the first one`() {
        val moved = layout(
            active = emptyList(),
            available = listOf(concrete, pending, total),
        ).move(fromKey = total.key, toKey = EDIT_ACTIVE_PLACEHOLDER_KEY)

        assertEquals(listOf(total.key), moved.activeKeys)
        assertEquals(listOf(concrete.key, pending.key), moved.availableKeys)
    }

    @Test
    fun `the empty active slot is refused once something is active`() {
        val before = layout(
            active = listOf(total),
            available = listOf(overall),
        )

        // The slot is not rendered in this state, so reaching it means the gesture outlived the
        // list it started on. Nothing moves.
        assertEquals(before, before.move(fromKey = overall.key, toKey = EDIT_ACTIVE_PLACEHOLDER_KEY))
    }

    // --- Drops that name nothing ------------------------------------------------------------------

    @Test
    fun `a component that is in neither list moves nothing`() {
        val before = layout(
            active = listOf(total),
            available = listOf(overall),
        )

        assertEquals(before, before.move(fromKey = concrete.key, toKey = total.key))
    }

    @Test
    fun `a destination that is in neither list moves nothing`() {
        val before = layout(
            active = listOf(total),
            available = listOf(overall),
        )

        assertEquals(before, before.move(fromKey = total.key, toKey = concrete.key))
    }

    @Test
    fun `a component dropped on itself stays where it is`() {
        val before = layout(
            active = listOf(total, overall),
            available = listOf(concrete),
        )

        assertEquals(before, before.move(fromKey = overall.key, toKey = overall.key))
    }
}
