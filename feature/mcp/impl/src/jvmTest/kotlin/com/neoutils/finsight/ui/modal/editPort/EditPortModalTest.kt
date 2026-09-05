package com.neoutils.finsight.ui.modal.editPort

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **When applying a port does something, and when it would do nothing.**
 *
 * The sheet refuses to close having done nothing, which is why the number already in use cannot be
 * confirmed. That reasoning has an exception it did not make: after a bind that failed, the port in
 * the field is the one the server wants and does not hold, so applying it again is the retry — and
 * `setPort` documents itself as exactly that, the way out of `PORT_IN_USE` once the user has freed
 * the port.
 */
class EditPortModalTest {

    private companion object {
        const val CURRENT = 8477
    }

    @Test
    fun `another port can be applied`() {
        assertTrue(canApplyPort(draft = "8500", current = CURRENT, isFailed = false))
    }

    @Test
    fun `the port already in use cannot be applied again`() {
        assertFalse(
            canApplyPort(draft = CURRENT.toString(), current = CURRENT, isFailed = false),
            "the sheet would have closed having done nothing",
        )
    }

    @Test
    fun `the port can be applied again once its bind has failed`() {
        assertTrue(
            canApplyPort(draft = CURRENT.toString(), current = CURRENT, isFailed = true),
            "the retry the failure's own way out asks for was refused, leaving the user with " +
                "nothing on this sheet to act on",
        )
    }

    @Test
    fun `another port can still be applied after a failure`() {
        assertTrue(canApplyPort(draft = "8500", current = CURRENT, isFailed = true))
    }

    @Test
    fun `what is not a port is never applied`() {
        assertFalse(canApplyPort(draft = "", current = CURRENT, isFailed = false))
        assertFalse(canApplyPort(draft = "0", current = CURRENT, isFailed = false))
        assertFalse(canApplyPort(draft = "70000", current = CURRENT, isFailed = false))
        assertFalse(canApplyPort(draft = "0", current = CURRENT, isFailed = true), "a failure does not make a non-port a port")
    }

    /**
     * The sheet offers what the app can take, and the privileged range is not that. A number the
     * user can confirm and the process cannot bind ends in a refusal the platform reports as the
     * port being held, which sends them to close a program that is not running.
     */
    @Test
    fun `a privileged port is never applied`() {
        assertFalse(canApplyPort(draft = "80", current = CURRENT, isFailed = false))
        assertFalse(canApplyPort(draft = "1023", current = CURRENT, isFailed = false))
        assertFalse(
            canApplyPort(draft = "80", current = CURRENT, isFailed = true),
            "a failure does not make a port the process cannot bind worth retrying",
        )
        assertTrue(
            canApplyPort(draft = "1024", current = CURRENT, isFailed = false),
            "the first port the app can take was refused along with the ones it cannot",
        )
    }
}
