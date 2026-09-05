@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.mcpActivity

import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.ClearAgentActivityUseCase
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.feature.mcp.api.IAgentActivityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * **The full log costs the same whether it holds one entry or five thousand.**
 *
 * The screen shows up to `AgentActivityRetention.MAX_ENTRIES` rows, and each one asks a single
 * question of the ledger — *does the posting this entry names still exist* — to decide whether to
 * offer a door to it. Asked per row, that question is a read per row, re-run in full on every
 * emission, because any insert or prune invalidates the flow the whole list is mapped from.
 *
 * What is asserted here is the shape of the cost and not the shape of the answer: the number of
 * reads the mapping needs **must not grow with the number of rows**. Which read that is — one
 * identity at a time, a batch, or a query that returns only the ids — is the implementation's to
 * choose, and this test deliberately does not name it.
 */
class McpActivityViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `mapping the log does not ask the ledger once per row`() = runTest {
        val oneRow = readsToMap(rows = 1)
        val manyRows = readsToMap(rows = 200)

        assertEquals(
            oneRow,
            manyRows,
            "mapping 200 rows cost $manyRows reads against $oneRow for a single row, so the cost " +
                "of opening the log grows with the history it is a log of",
        )
    }

    /**
     * The reads a page needs are a handful, not a multiple of the page. Stated separately from the
     * comparison above so that a mapping which is merely *consistently* expensive is caught too.
     */
    @Test
    fun `a page of the log is answered in a bounded number of reads`() = runTest {
        assertTrue(
            readsToMap(rows = 200) <= MAX_READS_PER_PAGE,
            "a page took more than $MAX_READS_PER_PAGE reads of the ledger",
        )
    }

    /**
     * The answer itself, so that the cost above cannot be paid by skipping the question: an entry
     * whose posting is gone must still say so, which is the whole reason the read exists.
     */
    @Test
    fun `every row is still mapped, and a removed posting is still reported as gone`() = runTest {
        val ledger = RecordingTransactions(existing = setOf(1L, 3L))
        val log = FakeActivityLog((1L..4L).map(::entryNaming))
        val state = stateOf(log, ledger)

        assertEquals(4, state.entries.size, "rows went missing from the log")
        assertEquals(
            listOf(false, true, false, true),
            state.entries.map { it.isTargetGone },
            "the log stopped telling a posting that is there from one that was removed",
        )
        assertFalse(state.isLoading)
    }

    private suspend fun readsToMap(rows: Int): Int {
        val ledger = RecordingTransactions(existing = (1L..rows).toSet())
        val state = stateOf(FakeActivityLog((1L..rows).map(::entryNaming)), ledger)

        // Without this the cost assertions could be satisfied by a state that had not been mapped
        // yet — nothing read, because nothing happened — and they would be measuring the initial
        // value rather than the work.
        assertEquals(rows, state.entries.size, "the log was not mapped, so its cost was not measured")
        return ledger.reads
    }

    /**
     * The mapped state, awaited rather than read off the flow.
     *
     * Awaited because what dispatcher the mapping runs on is the implementation's to choose: a
     * synchronous read of the current value would answer the initial state the moment the mapping
     * moves off the collector, and every assertion below it would pass having measured nothing.
     */
    private suspend fun stateOf(
        log: FakeActivityLog,
        ledger: RecordingTransactions,
    ): McpActivityUiState {
        val viewModel = McpActivityViewModel(log, ledger, ClearAgentActivityUseCase(log))
        return try {
            viewModel.uiState.first { !it.isLoading }
        } finally {
            // `WhileSubscribed` keeps the upstream alive for five seconds after the last
            // subscriber leaves, and the scope holding it outlives the test that made it — long
            // enough to be resumed after `Dispatchers.resetMain`, where it throws into whichever
            // test is running by then.
            viewModel.viewModelScope.cancel()
        }
    }

    private fun entryNaming(transactionId: Long) = AgentActivity(
        id = transactionId,
        at = Instant.fromEpochMilliseconds(transactionId),
        operation = "create_transaction",
        summary = "create transaction",
        outcome = AgentActivity.Outcome.APPLIED,
        reference = AgentActivity.Reference(
            kind = AgentActivity.Reference.Kind.TRANSACTION,
            id = transactionId,
        ),
    )

    private companion object {
        /**
         * A ceiling, not a target. It is loose on purpose: the point is that the number is a
         * constant, and pinning it to the exact count of whichever implementation is in place
         * would make this test fail for a change that is not a regression.
         */
        const val MAX_READS_PER_PAGE = 3
    }
}

/**
 * A ledger that counts what the mapping asks of it.
 *
 * **Every read added here must increment [reads].** The test above compares that number across page
 * sizes, so a read that does not count is a read the test cannot see.
 */
private class RecordingTransactions(
    private val existing: Set<Long>,
) : ITransactionRepository {

    var reads = 0
        private set

    override suspend fun getTransactionById(id: Long): Transaction? {
        reads++
        return existing.takeIf { id in it }?.let { transaction(id) }
    }

    override suspend fun getAllTransactions(): List<Transaction> {
        reads++
        return existing.map(::transaction)
    }

    override suspend fun getTransactionsBetween(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<Transaction> {
        reads++
        return existing.map(::transaction).filter { it.date in startDate..endDate }
    }

    override suspend fun getExistingTransactionIds(ids: Collection<Long>): Set<Long> {
        reads++
        return ids.intersect(existing)
    }

    private fun transaction(id: Long) = Transaction(id = id, title = null, date = LocalDate(2026, 1, 1))

    override fun observeAllTransactions() = unsupported()
    override fun observeTransactionsBy(date: LocalDate?, dimensionId: Long?, accountId: Long?) = unsupported()
    override fun observeTransactionById(id: Long) = unsupported()
    override suspend fun createTransaction(intent: TransactionIntent) = unsupported()
    override suspend fun createTransactions(intents: List<TransactionIntent>) = unsupported()
    override suspend fun getTransactionsByIds(ids: Collection<Long>): List<Transaction> =
        throw NotImplementedError()

    override suspend fun updateTransaction(
        id: Long,
        title: String?,
        date: LocalDate,
        legs: List<TransactionLeg>,
        contra: ContraLeg?,
    ) = unsupported()

    override suspend fun deleteTransactionById(id: Long) = unsupported()
    override suspend fun deleteTransactionsByIds(ids: List<Long>) = unsupported()

    private fun unsupported(): Nothing =
        throw UnsupportedOperationException("the log's mapping does not read this")
}

private class FakeActivityLog(entries: List<AgentActivity>) : IAgentActivityRepository {

    private val stored = MutableStateFlow(entries)

    override fun observeAll(): Flow<List<AgentActivity>> = stored
    override fun observeRecent(limit: Int): Flow<List<AgentActivity>> = stored

    override suspend fun record(
        operation: String,
        summary: String,
        outcome: AgentActivity.Outcome,
        detail: String?,
        reference: AgentActivity.Reference?,
    ): Long = 0

    override suspend fun clear() {
        stored.value = emptyList()
    }
}
