package com.neoutils.finsight.mcp

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **A refusal describes what the caller can change, and never asks for an argument back that the
 * call never gave.**
 *
 * Both edits carry: `update_transaction` takes the card from the posting when the call names none,
 * and `update_recurring` takes it from the template — and both take the direction the same way. A
 * refusal written for the declared case therefore reads, on a call that declared neither, as an
 * instruction to remove a `card_id` that is not there. Half of it is actionable and half of it
 * cannot be carried out, and an agent has no way to tell which half is which.
 *
 * The surface already answers this correctly one guard over: the refusal of a **carried category**
 * says what the posting brings and why the new direction cannot keep it, and asks for nothing to be
 * taken out. These are the same refusal read from the other side.
 */
class ARefusalNamesOnlyWhatTheCallGaveTest {

    @Test
    fun `flipping a card posting to income is refused for the card it sits on, not for a card_id`() =
        runTest {
            AgentWorld().use { world ->
                world.seedRegistration()

                world.overTheProtocol { client ->
                    val id = client.callTool(
                        "create_transaction",
                        """{"type":"expense","amount":100.00,"title":"Fone","card_id":1}""",
                    ).payload().at("transaction").identity()

                    val response = client.callTool(
                        "update_transaction",
                        """{"id":$id,"type":"income"}""",
                    )

                    assertTrue(response.isToolError(), "an income was left on a card")

                    val reason = assertNotNull(response.payload().text("reason"))

                    reason.assertAsksForNothingTheCallNeverGave()

                    assertTrue(
                        "Cartão" in reason,
                        "the refusal never says which card the posting sits on: $reason",
                    )
                }
            }
        }

    @Test
    fun `flipping a card template to income is refused for the card it is charged to`() = runTest {
        AgentWorld().use { world ->
            world.seedRegistration()

            world.overTheProtocol { client ->
                val id = client.callTool(
                    "create_recurring",
                    """{"type":"expense","amount":90.00,"day_of_month":5,"title":"Streaming","card_id":1}""",
                ).payload().at("recurring").identity()

                val response = client.callTool(
                    "update_recurring",
                    """{"id":$id,"type":"income"}""",
                )

                assertTrue(response.isToolError(), "an income template was left on a card")

                val reason = assertNotNull(response.payload().text("reason"))

                reason.assertAsksForNothingTheCallNeverGave()

                assertTrue(
                    "Cartão" in reason,
                    "the refusal never says which card the template is charged to: $reason",
                )
            }
        }
    }

    /**
     * The actionable half has to survive — an `account_id` is what the caller gives next — and the
     * half that cannot be carried out has to be gone.
     */
    private fun String.assertAsksForNothingTheCallNeverGave() {
        assertTrue(
            "card_id" !in this,
            "the refusal asks for a `card_id` back, and the call gave none: $this",
        )
        assertTrue(
            "account_id" in this,
            "the refusal drops the half the caller can act on: $this",
        )
    }
}
