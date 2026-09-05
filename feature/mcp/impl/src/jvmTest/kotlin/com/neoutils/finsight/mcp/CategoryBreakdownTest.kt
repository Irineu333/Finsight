package com.neoutils.finsight.mcp

import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.domain.model.Category
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Money with no category is a line of the breakdown, not a remainder.**
 *
 * It is the same total, with its share taken off the same scale, which is what makes the shares add
 * up to the month rather than to the classified part of it. A consumer handed only the categories
 * would report a smaller month, confidently, and nothing in the payload would disagree.
 *
 * It is also not a category: it has no identity, nothing to rename, archive or delete, and it is the
 * *absence* of a dimension in the ledger rather than a bucket account. Hence the identifier `0` and
 * a field of its own.
 */
class CategoryBreakdownTest {

    @Test
    fun `the unclassified spending is its own line, counted in the same whole`() = runTest {
        month().use { world ->
            world.overTheProtocol { client ->
                val payload = client.callTool("get_category_spending", MARCH).payload()

                assertEquals(400.00, payload.at("total").amount(), "the month's whole spending")

                val categories = payload["categories"]!!.jsonArray.map { it.jsonObject }
                assertEquals(listOf("Mercado"), categories.map { it.text("name") })
                assertEquals(300.00, categories.single().at("total").amount())
                assertEquals(0.75, categories.single().number("share"))

                val uncategorized = payload.at("uncategorized")
                assertEquals(0L.toDouble(), uncategorized.number("id"), "it has no identity to carry")
                assertEquals(100.00, uncategorized.at("total").amount())
                assertEquals(0.25, uncategorized.number("share"))

                assertEquals(
                    1.0,
                    categories.single().number("share")!! + uncategorized.number("share")!!,
                    "the shares no longer add up to the month",
                )
            }
        }
    }

    @Test
    fun `each line's figure is the ledger's, decomposed by currency`() = runTest {
        month().use { world ->
            world.overTheProtocol { client ->
                val payload = client.callTool("get_category_spending", MARCH).payload()
                val line = payload["categories"]!!.jsonArray.single().jsonObject

                assertEquals(mapOf("BRL" to 300.00), line.at("total").byCurrency())
                assertEquals(mapOf("BRL" to 400.00), payload.at("total").byCurrency())
            }
        }
    }

    @Test
    fun `the breakdown of both sides states the net, and one side alone does not`() = runTest {
        month().use { world ->
            world.overTheProtocol { client ->
                val both = client.callTool("get_spending_breakdown", MARCH).payload()
                assertEquals(5_000.00 - 400.00, both.at("net").amount())

                val expenseOnly = client
                    .callTool("get_spending_breakdown", """{"month":"2026-03","nature":"expense"}""")
                    .payload()
                assertEquals(null, expenseOnly["net"])
            }
        }
    }

    /**
     * The three tools that answer about categories say, each in its own description, what its recorte
     * is — so choosing between them does not mean calling all three and comparing the answers.
     */
    @Test
    fun `each of the three category tools says how it differs from the others`() = runTest {
        month().use { world ->
            world.overTheProtocol { client ->
                val announced = client.listTools()

                val spending = announced.announcedDescription(McpToolName.GET_CATEGORY_SPENDING.wireName)
                val income = announced.announcedDescription(McpToolName.GET_CATEGORY_INCOME.wireName)
                val breakdown = announced.announcedDescription(McpToolName.GET_SPENDING_BREAKDOWN.wireName)

                assertTrue(McpToolName.GET_CATEGORY_INCOME.wireName in spending, spending)
                assertTrue(McpToolName.GET_SPENDING_BREAKDOWN.wireName in spending, spending)
                assertTrue(McpToolName.GET_CATEGORY_SPENDING.wireName in income, income)
                assertTrue(
                    McpToolName.GET_CATEGORY_SPENDING.wireName in breakdown &&
                        McpToolName.GET_CATEGORY_INCOME.wireName in breakdown &&
                        "net" in breakdown,
                    breakdown,
                )
            }
        }
    }

    // ----------------------------------------------------------------------------------

    /** A month with a classified expense, an unclassified one, and a salary. */
    private suspend fun month(): AgentWorld {
        val world = AgentWorld()
        world.account(1, "Nubank")
        world.ledgerAccount(100, AccountEntity.Type.EXPENSE, "Despesas")
        world.ledgerAccount(200, AccountEntity.Type.INCOME, "Receitas")
        world.category(id = 1, dimensionId = 1, name = "Mercado")
        world.category(id = 2, dimensionId = 2, name = "Salário", type = Category.Type.INCOME)

        world.posting("2026-03-07", 1L posts -30_000, (100L posts 30_000).taggedWith(1))
        // No dimension on the nominal leg: this is the absence of a category, not a category.
        world.posting("2026-03-08", 1L posts -10_000, 100L posts 10_000)
        world.posting("2026-03-05", 1L posts 500_000, (200L posts -500_000).taggedWith(2))
        return world
    }

    private companion object {
        const val MARCH = """{"month":"2026-03"}"""
    }
}
