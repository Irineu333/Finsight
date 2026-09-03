package com.neoutils.finsight.mcp

import com.neoutils.finsight.mcp.McpServerHarness.Companion.freePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **An empty string names the field it is given for, and what it asks for is an erasure.**
 *
 * The complement of [ExplicitNullsOverTheProtocolTest], and the difference between them is the
 * whole subject: a key left out and a key carrying `null` both say *nothing*, while a key carrying
 * `""` says *this has none*. An edit that carries every field it was not given has to tell the two
 * apart, because filling the field back in from what is stored is the only reading under which a
 * title, once given, can never be taken back.
 *
 * They are driven over a real socket for the reason its sibling is: `ToolSupport.string` answers
 * `null` to a blank string exactly as it does to an absent key, so what is asserted here is whether
 * the tool asked `names` before believing that `null` — and the answer only exists once the
 * arguments have come off the wire.
 *
 * The third tool that carries a title, `confirm_recurring`, is not exercised here: it told the two
 * apart from the start, and `OperationsFamilyOverTheProtocolTest` already holds the proof.
 */
class EmptyStringsOverTheProtocolTest {

    /**
     * The scenario in full: a template with a title *and* a category is left named by the category
     * alone, which is a name the user chose and the app is happy to show.
     */
    @Test
    fun `an empty title erases the template's own, leaving it named by its category`() = runTest {
        withWorld({ seedOperations() }) { world, client ->
            val id = world.recurringId

            val response = client.callTool("update_recurring", """{"id":$id,"title":""}""")

            assertTrue(!response.isToolError(), "the edit was refused: ${response.toolText()}")
            assertNull(
                world.recurringRepository.getRecurringById(id)!!.title,
                "the empty title was read as silence, and the old one was written back",
            )
            assertEquals(
                Operations.CATEGORY_NAME,
                response.payload().at("recurring").text("title"),
                "the template is not named by the category it is left with",
            )
        }
    }

    /**
     * A title of spaces is a title of nothing — which the domain already settled, in
     * `displayTitleOrNull`, by reading a blank one as no title at all.
     */
    @Test
    fun `a blank title erases as an empty one does`() = runTest {
        withWorld({ seedOperations() }) { world, client ->
            val id = world.recurringId

            val response = client.callTool("update_recurring", """{"id":$id,"title":"   "}""")

            assertTrue(!response.isToolError(), "the edit was refused: ${response.toolText()}")
            assertNull(
                world.recurringRepository.getRecurringById(id)!!.title,
                "a title of spaces was read as silence, and the old one was written back",
            )
        }
    }

    /**
     * The other half of the erasure, and the reason it is safe to offer: the rule that a template
     * has a title or a category is the domain's, so taking the last of the two away is refused
     * *there* — the tool neither restates that rule nor gets to swallow the request before it.
     */
    @Test
    fun `erasing the last name a template has is refused, and the title stands`() = runTest {
        withWorld({ seedOperations() }) { world, client ->
            val id = world.recurringId

            val unclassified = client.callTool("update_recurring", """{"id":$id,"category_id":0}""")
            assertTrue(!unclassified.isToolError(), "the category was not dropped: ${unclassified.toolText()}")

            val response = client.callTool("update_recurring", """{"id":$id,"title":""}""")

            assertTrue(
                response.isToolError(),
                "a template with neither title nor category was written: ${response.toolText()}",
            )
            assertEquals(
                Operations.RECURRING_TITLE,
                world.recurringRepository.getRecurringById(id)!!.title,
                "the refused edit took the title anyway",
            )
        }
    }

    /** The same question of the other edit, against a posting the ledger already holds. */
    @Test
    fun `an empty title erases a posting's own, leaving it named by its category`() = runTest {
        withWorld({ seedRegistration() }) { world, client ->
            val id = world.groceriesId

            val response = client.callTool("update_transaction", """{"id":$id,"title":""}""")

            assertTrue(!response.isToolError(), "the edit was refused: ${response.toolText()}")
            assertNull(
                world.transactionRepository.getTransactionById(id)!!.title,
                "the empty title was read as silence, and the old one was written back",
            )
        }
    }

    /**
     * The posting's half of the same guarantee, owned by its own rule
     * (`ValidateTransactionFormUseCaseImpl`) rather than the template's.
     */
    @Test
    fun `erasing the last name a posting has is refused, and the title stands`() = runTest {
        withWorld({ seedRegistration() }) { world, client ->
            val id = world.groceriesId

            val response = client.callTool(
                "update_transaction",
                """{"id":$id,"title":"","category_id":0}""",
            )

            assertTrue(
                response.isToolError(),
                "a posting with neither title nor category was written: ${response.toolText()}",
            )
            assertEquals(
                "Mercado",
                world.transactionRepository.getTransactionById(id)!!.title,
                "the refused edit took the title anyway",
            )
        }
    }

    /**
     * The direction the erasure must not cost: an edit that says nothing about the title still
     * keeps it, which is what both tools promise in their answer.
     */
    @Test
    fun `a title the call never names is still kept by both edits`() = runTest {
        withWorld({ seedOperations() }) { world, client ->
            val response = client.callTool(
                "update_recurring",
                """{"id":${world.recurringId},"amount":50.0}""",
            )

            assertTrue(!response.isToolError(), "the edit was refused: ${response.toolText()}")
            assertEquals(
                Operations.RECURRING_TITLE,
                world.recurringRepository.getRecurringById(world.recurringId)!!.title,
                "a title nobody named was cleared",
            )
        }

        withWorld({ seedRegistration() }) { world, client ->
            val response = client.callTool(
                "update_transaction",
                """{"id":${world.groceriesId},"amount":50.0}""",
            )

            assertTrue(!response.isToolError(), "the edit was refused: ${response.toolText()}")
            assertEquals(
                "Mercado",
                world.transactionRepository.getTransactionById(world.groceriesId)!!.title,
                "a title nobody named was cleared",
            )
        }
    }

    /**
     * A real server over a real socket, with the world [seed] builds behind it — the same harness
     * the desktop assembles, and the same one each family's own test drives its tools through.
     */
    private suspend fun <T> withWorld(
        seed: suspend AgentWorld.() -> T,
        block: suspend (T, McpConversation) -> Unit,
    ) {
        AgentWorld().use { world ->
            val seeded = world.seed()

            val port = freePort()
            McpServerHarness(tools = world.tools()).use { harness ->
                harness.controller.setPort(port)
                harness.controller.setEnabled(true)
                val token = assertNotNull(harness.controller.token.value, "the server minted no token")

                withContext(Dispatchers.IO) {
                    block(seeded, McpConversation(port, token).open())
                }

                harness.controller.stop()
            }
        }
    }
}
