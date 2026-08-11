package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.ui.util.WindowMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Each component declares the window modes it is shown in, and the screen offers what the mode
 * it is in declares. What is pinned here is the one component that narrows its list, and that no
 * other silently narrowed one — a component shown nowhere is a component nobody can reach.
 */
class DashboardComponentModesTest {

    @Test
    fun `the quick-actions grid belongs to the layout without a rail`() {
        assertEquals(setOf(WindowMode.COMPACT), DashboardComponentType.QUICK_ACTIONS.modes)
    }

    @Test
    fun `every other component adapts to all of them`() {
        val narrowed = DashboardComponentType.entries
            .filterNot { it == DashboardComponentType.QUICK_ACTIONS }
            .filterNot { it.modes == WindowMode.ALL }

        assertEquals(emptyList(), narrowed)
    }

    @Test
    fun `no component is left without a mode to be shown in`() {
        assertTrue(DashboardComponentType.entries.all { it.modes.isNotEmpty() })
    }
}
