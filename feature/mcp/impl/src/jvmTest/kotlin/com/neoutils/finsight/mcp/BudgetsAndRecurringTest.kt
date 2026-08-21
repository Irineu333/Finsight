package com.neoutils.finsight.mcp

import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.domain.model.Budget
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

    private companion object {
        const val MARCH = """{"month":"2026-03"}"""
    }
}
