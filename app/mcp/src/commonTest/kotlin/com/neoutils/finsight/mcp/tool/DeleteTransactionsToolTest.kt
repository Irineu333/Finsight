@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.mcp.tool

import arrow.core.Either
import com.neoutils.finsight.domain.error.ClosedAccountException
import com.neoutils.finsight.domain.error.ClosedFacade
import com.neoutils.finsight.domain.error.LedgerError
import com.neoutils.finsight.mcp.FakeTransactionRepository
import com.neoutils.finsight.mcp.FixedClock
import com.neoutils.finsight.mcp.RecordingDeleteTransaction
import com.neoutils.finsight.mcp.RecordingJournal
import com.neoutils.finsight.mcp.account
import com.neoutils.finsight.mcp.category
import com.neoutils.finsight.mcp.contract.ToolErrorCategory
import com.neoutils.finsight.mcp.contract.ToolOutcome
import com.neoutils.finsight.mcp.entry
import com.neoutils.finsight.mcp.expensesAccount
import com.neoutils.finsight.mcp.transaction
import com.neoutils.finsight.mcp.write.ActivityRecorder
import com.neoutils.finsight.mcp.write.IdempotencyStore
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class DeleteTransactionsToolTest {

    private val checking = account(1, "Checking", currency = "BRL", isDefault = true)
    private val groceries = category(20, "Groceries")

    private val first = transaction(
        id = 1,
        date = LocalDate(2026, 6, 28),
        entries = listOf(entry(checking, -1_000), entry(expensesAccount, 1_000, groceries.dimensionId)),
    )

    private val second = first.copy(id = 2)

    private val journal = RecordingJournal()

    @Test
    fun `it removes every item through the use case that owns the removal`() = runTest {
        val deleting = RecordingDeleteTransaction()
        val outcome = tool(deleting).execute(arguments("""{"items":[{"transactionId":1},{"transactionId":2}]}"""))

        val result = assertIs<ToolOutcome.Ok>(outcome).result
        assertEquals(2, result["appliedCount"]!!.jsonPrimitive.content.toInt())
        assertEquals(listOf(1L, 2L), deleting.removed.map { it.id })
    }

    @Test
    fun `an item the domain protects is refused, and the rest of the batch is removed`() = runTest {
        val deleting = RecordingDeleteTransaction { transaction ->
            if (transaction.id == 1L) {
                Either.Left(ClosedAccountException(LedgerError.ClosedAccountRemoval(ClosedFacade.ACCOUNT)))
            } else {
                Either.Right(Unit)
            }
        }
        val outcome = tool(deleting).execute(arguments("""{"items":[{"transactionId":1},{"transactionId":2}]}"""))

        val items = assertIs<ToolOutcome.Ok>(outcome).result["items"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("REFUSED", "APPLIED"), items.map { it["status"]!!.jsonPrimitive.content })
        assertEquals(
            ToolErrorCategory.DOMAIN_RULE.name,
            items[0]["error"]!!.jsonObject["category"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `an unknown transaction is refused as not found, and nothing is removed for it`() = runTest {
        val deleting = RecordingDeleteTransaction()
        val outcome = tool(deleting).execute(arguments("""{"items":[{"transactionId":404}]}"""))

        val item = assertIs<ToolOutcome.Ok>(outcome).result["items"]!!.jsonArray.single().jsonObject
        assertEquals(BatchCodes.TRANSACTION_NOT_FOUND, item["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
        assertTrue(deleting.removed.isEmpty())
    }

    @Test
    fun `repeating a removal under the same key removes nothing again`() = runTest {
        val deleting = RecordingDeleteTransaction()
        val tool = tool(deleting)
        val call = arguments("""{"idempotencyKey":"cleanup","items":[{"transactionId":1}]}""")

        tool.execute(call)
        tool.execute(call)

        assertEquals(1, deleting.removed.size)
    }

    @Test
    fun `it announces itself as destructive`() {
        val annotations = tool(RecordingDeleteTransaction()).annotations

        assertEquals(false, annotations.readOnlyHint)
        assertEquals(true, annotations.destructiveHint)
    }

    @Test
    fun `one call leaves one record naming every transaction it removed`() = runTest {
        tool(RecordingDeleteTransaction()).execute(
            arguments("""{"items":[{"transactionId":1},{"transactionId":2}]}"""),
        )

        val record = journal.records.single()
        assertEquals(DELETE_TRANSACTIONS_TOOL, record.tool)
        assertEquals(listOf("transaction:1", "transaction:2"), record.affected)
    }

    private fun tool(deleteTransaction: RecordingDeleteTransaction): DeleteTransactionsTool {
        val clock = FixedClock(Instant.parse("2026-06-28T12:00:00Z"))

        return DeleteTransactionsTool(
            transactions = FakeTransactionRepository(listOf(first, second)),
            deleteTransaction = deleteTransaction,
            idempotency = IdempotencyStore(clock),
            activity = ActivityRecorder(journal, clock),
        )
    }

    private fun arguments(raw: String): JsonObject = Json.parseToJsonElement(raw) as JsonObject
}
