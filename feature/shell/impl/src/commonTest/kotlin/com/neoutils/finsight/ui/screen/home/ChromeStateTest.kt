package com.neoutils.finsight.ui.screen.home

import com.neoutils.finsight.feature.shell.api.ChromeConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **Crossing the breakpoint is a chrome change like any other, so it has to be a change of state.**
 *
 * Every answer here is a fact about the pair — the configuration a destination published *and* the
 * room the window gives — and about neither half alone. That is the whole argument for keeping the
 * width inside the value the shell's `Transition` animates: an answer that is a fact about the pair
 * cannot be a branch around the composable, because a branch inserts and removes where a transition
 * would animate.
 *
 * The published configuration is held fixed in each case on purpose. Nothing the destination says
 * changes when a window is dragged, and the chrome changes completely.
 */
class ChromeStateTest {

    private val wide = ChromeState(ChromeConfig.Default, isWideWindow = true)
    private val compact = wide.copy(isWideWindow = false)

    @Test
    fun `the same offer is a bar in one width and a rail in the other`() {
        assertTrue(compact.isBottomBarVisible)
        assertFalse(compact.isRailVisible)

        assertTrue(wide.isRailVisible)
        assertFalse(wide.isBottomBarVisible)
    }

    @Test
    fun `the button beside the content exists only where there is a rail to hold it`() {
        assertTrue(wide.isRailButtonVisible)
        assertFalse(compact.isRailButtonVisible)
    }

    @Test
    fun `a screen that withdraws the selector withdraws both of its shapes`() {
        val withdrawn = ChromeConfig.Default.copy(isBottomBarVisible = false)

        assertFalse(compact.copy(config = withdrawn).isBottomBarVisible)
        assertFalse(wide.copy(config = withdrawn).isRailVisible)
    }

    @Test
    fun `a screen that withdraws the button withdraws the rail's header too`() {
        assertFalse(wide.copy(config = ChromeConfig.ContentOnly).isRailButtonVisible)

        // `NoButtonOverContent` is the other answer, and it is not this one: the button is refused
        // the corner it would float in, never the header it stands beside.
        assertTrue(wide.copy(config = ChromeConfig.NoButtonOverContent).isRailButtonVisible)
    }
}
