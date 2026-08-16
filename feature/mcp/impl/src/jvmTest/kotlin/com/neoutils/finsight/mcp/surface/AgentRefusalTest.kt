package com.neoutils.finsight.mcp.surface

import com.neoutils.finsight.domain.error.RetireError
import com.neoutils.finsight.mcp.McpToolName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **A refusal says why, and — where the domain allows something else — says what.**
 *
 * A refusal that only says no is what teaches an agent to look for a way round it. The one this
 * surface was designed against actually happened: told it could not delete a posting, an agent
 * reported that the app has no such capability, and considered editing the amount to zero instead —
 * which would have left a zero-value row in every listing and every count, out of the totals and
 * still in the history.
 */
class AgentRefusalTest {

    @Test
    fun `an identity that matches nothing is named, not merely refused`() {
        val refusal = AgentRefusal.notFound(kind = "category", id = 42L)

        assertTrue("category" in refusal.reason && "42" in refusal.reason)
        assertNull(refusal.tryInstead, "nothing else does what a wrong identifier was asking for")
    }

    @Test
    fun `a removal the domain preserves names archiving as the way through`() {
        val refusal = AgentRefusal.cannotRemove(RetireError.HAS_TRANSACTIONS.message)

        assertEquals(RetireError.HAS_TRANSACTIONS.message, refusal.reason)
        assertEquals(McpToolName.ARCHIVE_ENTITY.wireName, refusal.tryInstead)
    }

    @Test
    fun `the reason is the domain's own words and not a second wording of them`() {
        // Every error in the app already states its reason in English. A refusal that reworded it
        // would be a second answer to "why not", one edit away from disagreeing with the first.
        RetireError.entries.forEach {
            assertEquals(it.message, AgentRefusal.cannotRemove(it.message).reason)
        }
    }

    @Test
    fun `a refusal with nothing to offer offers nothing`() {
        assertNull(AgentRefusal(reason = "Cannot edit an operation with more than one monetary leg.").tryInstead)
    }
}
