package com.neoutils.finsight.mcp

import com.neoutils.finsight.mcp.McpServerHarness.Companion.freePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **An explicit `null` says nothing, and every reader of the wire has to agree on that.**
 *
 * Most clients serialise an optional field they were given nothing for as `"month": null` rather
 * than by leaving the key out, so the two arrive at the server for the same intention and have to
 * mean the same thing. They are driven over a real socket because a `null` only exists once the
 * arguments have been parsed from JSON: a test that handed a tool a hand-built `JsonObject` would
 * be asserting about the object it built.
 *
 * What is asserted here is never *which* default applies — that belongs to each tool and to the
 * families that exercise them — but only that the tool reached its default at all instead of
 * reading the four-character string `null` as the user's answer.
 */
class ExplicitNullsOverTheProtocolTest {

    /** The world's day is 15 March 2026, so "the month the app is in" is a month with postings in it. */
    @Test
    fun `an explicit null month falls back to the month the app is in`() = runTest {
        withWorld({ seedRegistration() }) { _, client ->
            val response = client.callTool("list_transactions", """{"month":null}""")

            assertTrue(!response.isToolError(), "a null month was refused: ${response.toolText()}")
            assertEquals(
                "2026-03",
                response.payload().at("period").text("month"),
                "a null month was read as a month, instead of as no month at all",
            )
            assertEquals(
                client.callTool("list_transactions", """{"month":"2026-03"}""").payload(),
                response.payload(),
                "the fallback answered something other than the month the app is in",
            )
        }
    }

    /** A name is required, and a `null` is not one — least of all the name `null`. */
    @Test
    fun `an explicit null name is refused, rather than naming a category null`() = runTest {
        withWorld({ seedRegistration() }) { world, client ->
            val response = client.callTool("create_category", """{"name":null,"type":"expense"}""")

            assertTrue(response.isToolError(), "a null name was accepted: ${response.toolText()}")
            assertTrue(
                world.categoryRepository.getAllCategoriesIncludingClosed().none { it.name == "null" },
                "a category literally called `null` was created",
            )
        }
    }

    /**
     * The edit promises that *"what is not given keeps the value it already has"*, and a `null` is
     * not given.
     */
    @Test
    fun `an explicit null on an edit keeps the value the posting already has`() = runTest {
        withWorld({ seedRegistration() }) { world, client ->
            val id = world.groceriesId

            val response = client.callTool("update_transaction", """{"id":$id,"title":null}""")

            assertTrue(!response.isToolError(), "the edit was refused: ${response.toolText()}")
            assertEquals(
                "Mercado",
                world.transactionRepository.getTransactionById(id)!!.title,
                "the title was rewritten to what the null spelled",
            )
        }
    }

    /**
     * An amount is read from the raw element rather than as a word, so a `null` there is the one
     * that comes back as *"must be an amount"* — a refusal of a call that named no amount at all.
     */
    @Test
    fun `an explicit null amount is read as absent, not refused as malformed`() = runTest {
        withWorld({ seedRegistration() }) { world, client ->
            val id = world.groceriesId

            val response = client.callTool(
                "update_transaction",
                """{"id":$id,"amount":null,"title":"Feira"}""",
            )

            assertTrue(!response.isToolError(), "a null amount was refused: ${response.toolText()}")

            val stored = world.transactionRepository.getTransactionById(id)!!
            assertEquals(300.00, stored.amount, "the amount nobody named was rewritten")
            assertEquals("Feira", stored.title, "the rest of the edit did not go through")
        }
    }

    /** The flag an edit may leave out — and a `null` leaves it out rather than clearing it. */
    @Test
    fun `an explicit null flag keeps the flag, rather than being refused or read as false`() = runTest {
        withWorld({ seedRegistration() }) { world, client ->
            val response = client.callTool("update_account", """{"id":1,"is_default":null}""")

            assertTrue(!response.isToolError(), "a null flag was refused: ${response.toolText()}")
            assertEquals(
                true,
                world.accountRepository.getAccountById(1)!!.isDefault,
                "a flag nobody named was cleared",
            )
        }
    }

    /** A `null` in place of a list is no list; a `null` *inside* one would still be malformed. */
    @Test
    fun `an explicit null list is read as absent, not as a malformed list`() = runTest {
        withWorld({ seedRegistration() }) { _, client ->
            val response = client.callTool("get_balance", """{"exclude_account_ids":null}""")

            assertTrue(!response.isToolError(), "a null list was refused: ${response.toolText()}")
            assertEquals(
                client.callTool("get_balance", "{}").payload().at("balance"),
                response.payload().at("balance"),
                "the null list excluded something, instead of excluding nothing",
            )
        }
    }

    /**
     * The one place absence and emptiness are different intentions: a cycle pre-fills from its
     * template, and a `null` is the caller saying nothing about the title rather than erasing it.
     */
    @Test
    fun `an explicit null names no field, so the template's own title stands`() = runTest {
        withWorld({ seedOperations() }) { world, client ->
            val response = client.callTool(
                "confirm_recurring",
                """{"id":${world.recurringId},"title":null}""",
            )

            assertTrue(!response.isToolError(), "the cycle was refused: ${response.toolText()}")
            assertEquals(
                Operations.RECURRING_TITLE,
                response.payload().at("transaction").text("title"),
                "the null was taken for a title the caller chose",
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
