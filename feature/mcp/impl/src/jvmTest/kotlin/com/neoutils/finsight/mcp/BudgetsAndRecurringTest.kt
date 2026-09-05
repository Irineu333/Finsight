package com.neoutils.finsight.mcp

import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.TransactionType
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The two questions whose answer the tool has to **compose** and must not decide.
 *
 * `CalculateBudgetProgressUseCase` wants three lists it does not fetch — the budgets, the templates
 * and the transactions — because a percentage limit is a share of whatever the recurring behind it
 * was confirmed at *in that month*. Gathering them is the tool's work; what a limit resolves to and
 * what counts against it is not.
 *
 * `GetPendingRecurringUseCase` decides what "pending" means: the day has come and the cycle for that
 * month is neither confirmed nor skipped. The tool supplies the date and the two lists, and adds the
 * one thing the domain does not — the total of what confirming them would post, which is money that
 * has **not moved**.
 */
class BudgetsAndRecurringTest {

    @Test
    fun `a budget reports what is spent against it, what is left and how far along it is`() = runTest {
        world().use { world ->
            world.budgets += Budget(
                id = 1,
                title = "Mercado do mês",
                categories = listOf(world.categories.single { it.name == "Mercado" }),
                iconKey = "cart",
                amount = 500.00,
                currency = "BRL",
                createdAt = 0,
            )

            world.overTheProtocol { client ->
                val budget = client.callTool("get_budget_progress", MARCH)
                    .payload()["budgets"]!!.jsonArray.single().jsonObject

                assertEquals("Mercado do mês", budget.text("title"))
                assertEquals(listOf("Mercado"), budget["categories"]!!.jsonArray.map { it.toString().trim('"') })
                assertEquals(500.00, budget.at("limit").amount())
                assertEquals(300.00, budget.at("spent").amount())
                assertEquals(200.00, budget.at("remaining").amount())
                assertEquals(0.6, budget.number("progress"))
                assertEquals("fixed", budget.text("limit_type"))
            }
        }
    }

    /**
     * The two figures the bar is drawn from are truncated on purpose, so on their own they say the
     * same thing about a budget that stopped exactly at its ceiling and one that went past it. The
     * overrun is a fact of its own, and the payload has to carry it or the agent reports the safer
     * of the two readings to whoever got the other one.
     */
    @Test
    fun `a budget past its limit is told apart from one that stopped exactly at it`() = runTest {
        world().use { world ->
            val mercado = world.categories.single { it.name == "Mercado" }
            world.budgets += budget(id = 1, title = "No limite", limit = 300.00, category = mercado)
            world.budgets += budget(id = 2, title = "Estourado", limit = 200.00, category = mercado)

            world.overTheProtocol { client ->
                val budgets = client.callTool("get_budget_progress", MARCH)
                    .payload()["budgets"]!!.jsonArray
                    .map { it.jsonObject }
                    .associateBy { it.text("title") }

                val atTheLimit = budgets.getValue("No limite")
                val over = budgets.getValue("Estourado")

                // What the two have in common, and why nothing else in the payload separates them.
                assertEquals(0.00, atTheLimit.at("remaining").amount())
                assertEquals(0.00, over.at("remaining").amount())
                assertEquals(1.0, atTheLimit.number("progress"))
                assertEquals(1.0, over.number("progress"))

                assertEquals(false, atTheLimit.flag("is_exceeded"), "spending its ceiling is not passing it")
                assertNull(atTheLimit["exceeded_by"], "nothing was exceeded, so there is no figure for it")

                assertEquals(true, over.flag("is_exceeded"), "a budget past its limit reads as one at it")
                assertEquals(100.00, over.at("exceeded_by").amount(), "R$ 300 spent against R$ 200")
                assertEquals("BRL", over.at("exceeded_by").currency())
            }
        }
    }

    /**
     * With part of the spending in a currency no rate reaches, `spent` is a **floor**, and a floor
     * settles nothing against a limit. The domain refuses to call that exceeded, and refusing to
     * call it *not* exceeded is the same refusal — a `false` here would be the payload asserting
     * what nothing established, in the one direction a budget must never err in.
     */
    @Test
    fun `spending no rate reaches leaves the overrun unstated, rather than denied`() = runTest {
        unpricedWorld().use { world ->
            world.budgets += budget(
                id = 1,
                title = "Mercado",
                limit = 200.00,
                category = world.categories.single { it.name == "Mercado" },
            )

            world.overTheProtocol { client ->
                val budget = client.callTool("get_budget_progress", MARCH)
                    .payload()["budgets"]!!.jsonArray.single().jsonObject

                assertNull(budget["progress"], "a bar that cannot be drawn is not a bar at one")
                assertNull(budget["is_exceeded"], "a floor that rules nothing out ruled out an overrun")
                assertNull(budget["exceeded_by"])
            }
        }
    }

    @Test
    fun `the pending cycles are the ones whose day has come and that nobody handled`() = runTest {
        world().use { world ->
            val account = world.accounts.single()
            world.recurringList += Recurring(
                id = 1,
                type = TransactionType.EXPENSE,
                amount = 120.00,
                title = "Internet",
                dayOfMonth = 10,
                category = null,
                account = account,
                creditCard = null,
                createdAt = 0,
            )
            world.recurringList += Recurring(
                id = 2,
                type = TransactionType.EXPENSE,
                amount = 80.00,
                title = "Academia",
                // Later in the month than the day the app is on: due, but not yet.
                dayOfMonth = 25,
                category = null,
                account = account,
                creditCard = null,
                createdAt = 0,
            )
            world.recurringList += Recurring(
                id = 3,
                type = TransactionType.EXPENSE,
                amount = 40.00,
                title = "Streaming",
                dayOfMonth = 5,
                category = null,
                account = account,
                creditCard = null,
                createdAt = 0,
            )
            // The third one's cycle was already confirmed this month, so it is not waiting.
            world.occurrences += RecurringOccurrence(
                id = 1,
                recurringId = 3,
                cycleNumber = 1,
                yearMonth = YearMonth(2026, 3),
                status = RecurringOccurrence.Status.CONFIRMED,
                effectiveDate = LocalDate(2026, 3, 5),
                handledAt = 0,
            )

            world.overTheProtocol { client ->
                val payload = client.callTool("get_pending_recurring").payload()
                val pending = payload["pending"]!!.jsonArray.map { it.jsonObject }

                assertEquals(listOf("Internet"), pending.map { it.text("title") })
                assertEquals(120.00, pending.single().at("amount").amount())
                assertEquals("BRL", pending.single().at("amount").currency())
                assertEquals(true, pending.single().flag("is_pending"))
                assertEquals("2026-03-10", pending.single().text("next_occurrence"))

                assertEquals(
                    120.00,
                    payload.at("expected_total").amount(),
                    "the total is what confirming the pending cycles would post",
                )
                assertTrue(
                    "not" in payload.at("perimeter").text("covers").orEmpty().lowercase() ||
                        payload.at("perimeter")["excludes"]!!.jsonArray.toString().contains("confirmed"),
                    "the answer does not say these are intentions rather than postings: " +
                        payload.at("perimeter"),
                )
            }
        }
    }

    /** A template with no account or card cannot be denominated, and is in no total. */
    @Test
    fun `a template pointing nowhere has no currency, and says so instead of guessing one`() = runTest {
        world().use { world ->
            world.recurringList += Recurring(
                id = 1,
                type = TransactionType.EXPENSE,
                amount = 90.00,
                title = "Órfã",
                dayOfMonth = 1,
                category = null,
                account = null,
                creditCard = null,
                createdAt = 0,
            )

            world.overTheProtocol { client ->
                val payload = client.callTool("get_pending_recurring").payload()
                val orphan = payload["pending"]!!.jsonArray.single().jsonObject

                assertEquals(null, orphan.at("amount")["currency"], "a currency was invented")
                assertTrue(
                    "no account or card" in orphan.at("amount").at("limitation")
                        .text("explanation").orEmpty(),
                    "the figure does not say why it has no currency: ${orphan.at("amount")}",
                )
                assertEquals(
                    0.0,
                    payload.at("expected_total").amount(),
                    "an amount in no currency cannot be added to a total",
                )
            }
        }
    }

    // ----------------------------------------------------------------------------------

    /** One account, one expense category, and R$ 300 spent under it in March. */
    private suspend fun world(): AgentWorld {
        val world = AgentWorld(today = LocalDate(2026, 3, 15))
        world.account(1, "Nubank")
        world.ledgerAccount(100, AccountEntity.Type.EXPENSE, "Despesas")
        world.category(id = 1, dimensionId = 1, name = "Mercado")
        world.posting("2026-03-07", 1L posts -30_000, (100L posts 30_000).taggedWith(1))
        return world
    }

    /** The same month, spent in dollars, with nothing in the archive to price them in reais. */
    private suspend fun unpricedWorld(): AgentWorld {
        val world = AgentWorld(
            today = LocalDate(2026, 3, 15),
            rates = emptyMap(),
            currenciesInUse = listOf("BRL", "USD"),
        )
        world.account(1, "Wise", currency = "USD")
        world.ledgerAccount(100, AccountEntity.Type.EXPENSE, "Expenses", currency = "USD")
        world.category(id = 1, dimensionId = 1, name = "Mercado")
        world.posting(
            "2026-03-07",
            (1L posts -30_000) inCurrency "USD",
            ((100L posts 30_000) inCurrency "USD").taggedWith(1),
        )
        return world
    }

    private fun budget(id: Long, title: String, limit: Double, category: Category) = Budget(
        id = id,
        title = title,
        categories = listOf(category),
        iconKey = "cart",
        amount = limit,
        currency = "BRL",
        createdAt = 0,
    )

    private companion object {
        const val MARCH = """{"month":"2026-03"}"""
    }
}
