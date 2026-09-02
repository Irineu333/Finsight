package com.neoutils.finsight.ui.screen.home

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **The six transitions of the action button, as the three answers that hold them.**
 *
 * The line joining the two visible places is travelled only in full view: every appearance and
 * disappearance is vertical, with no sideways component. Nothing in the shell enumerates the six —
 * turn the middle answer into "travel" and every vertical transition acquires a diagonal.
 */
class FabJourneyTest {

    @Test
    fun `the line is travelled only in full view`() {
        // On the bar → the corner, and back: the button is there at both ends of the change.
        assertEquals(
            FabJourney.Travel,
            fabJourney(isOnScreen = true, isWanted = true),
        )
    }

    @Test
    fun `a button on its way out keeps the place it stands in`() {
        // On the bar → hidden, and the corner → hidden: the bar leaves in the same breath, and
        // following it would carry the button sideways as it goes.
        assertEquals(
            FabJourney.Hold,
            fabJourney(isOnScreen = true, isWanted = false),
        )
    }

    @Test
    fun `a button that is not on screen is placed rather than sent`() {
        // Hidden → on the bar, and hidden → the corner: it rises into the place it belongs to.
        assertEquals(
            FabJourney.Place,
            fabJourney(isOnScreen = false, isWanted = true),
        )

        // And while it stays away the place follows the chrome for free: nobody can see it move.
        assertEquals(
            FabJourney.Place,
            fabJourney(isOnScreen = false, isWanted = false),
        )
    }
}
