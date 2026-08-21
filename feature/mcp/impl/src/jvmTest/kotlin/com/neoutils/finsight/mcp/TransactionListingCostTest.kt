package com.neoutils.finsight.mcp

import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.mcp.tool.ListTransactionsTool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * **What one month costs to answer is what that month holds — never what the ledger holds.**
 *
 * A listing is the one surface an agent walks: it asks for a month, reads a page, asks for the next.
 * If answering a page means materialising every posting the user has ever made, the second year of
 * use is twice as slow as the first for a question whose answer did not change size, and each extra
 * page pays it again. Nothing about the payload says so — the answer is correct, and only late.
 *
 * A cost cannot be asserted from one measurement: "it hydrated eighteen postings" is a number with
 * no requirement in it. So the same question is put to **two ledgers that differ only outside the
 * month asked for** — one small, one a hundred and sixty times larger — and what is asserted is
 * the comparison: the larger ledger must not cost more. That is the requirement, and it says
 * nothing about how it is met; a date-scoped read, a batched hydration or anything else that is
 * genuinely cheap satisfies it equally.
 *
 * And a cost assertion alone can be satisfied by a tool that does less work *and answers wrongly*,
 * so the page itself is asserted beside it: both ledgers must return the same March, and it must be
 * the March that was seeded.
 */
class TransactionListingCostTest {

    @Test
    fun `answering one month costs what the month holds, not what the ledger holds`() =
        runTest(timeout = 10.minutes) {
            ledgerOf(outsideTheMonth = SMALL_LEDGER).use { small ->
                ledgerOf(outsideTheMonth = LARGE_LEDGER).use { large ->
                    val overASmallLedger = small.readMarch()
                    val overALargeLedger = large.readMarch()

                    // The cheaper reading has to be the same reading: a page that grew cheap by
                    // answering something else is not the requirement.
                    assertEquals(MARCH.size, overASmallLedger.matching)
                    assertEquals(MARCH.size, overALargeLedger.matching)
                    assertEquals(marchNewestFirst(), overASmallLedger.page)
                    assertEquals(marchNewestFirst(), overALargeLedger.page)

                    assertTrue(
                        overALargeLedger.hydrated <= overASmallLedger.hydrated,
                        "the same March cost ${overALargeLedger.hydrated} hydrated postings over a " +
                            "ledger of ${LARGE_LEDGER + MARCH.size} and only " +
                            "${overASmallLedger.hydrated} over one of ${SMALL_LEDGER + MARCH.size} " +
                            "(${overALargeLedger.reads} and ${overASmallLedger.reads} reads of the " +
                            "transaction repository): the work of answering one month grows with " +
                            "what the ledger holds outside it",
                    )
                }
            }
        }

    // ----------------------------------------------------------------------------------

    /** One reading of March: what came back, and what the ledger was made to materialise for it. */
    private data class Reading(
        val matching: Int,
        val page: List<String>,
        val hydrated: Int,
        val reads: Int,
    )

    /**
     * March, asked of this world through the production tool, with the transaction port counted.
     *
     * Everything but that port is the world's own — the real ledger, the real entry reads, the
     * production consolidation — so what the counter sees is the listing's own appetite and nothing
     * the fixture invented.
     */
    private suspend fun AgentWorld.readMarch(): Reading {
        val counted = CountingTransactions(transactionRepository)
        val deps = dependencies()

        val tool = ListTransactionsTool(
            clock = deps.clock,
            transactionRepository = counted,
            entryRepository = deps.entryRepository,
            accountRepository = deps.accountRepository,
            categoryRepository = deps.categoryRepository,
            creditCardRepository = deps.creditCardRepository,
            installmentRepository = deps.installmentRepository,
            consolidateMoney = deps.consolidateMoney,
            baseCurrency = deps.baseCurrency,
        )

        val payload = json.parseToJsonElement(tool.call(ASK_FOR_MARCH).text).jsonObject

        return Reading(
            matching = payload.count("matching"),
            page = payload["transactions"]!!.jsonArray
                .map { it.jsonObject }
                .map { "${it.text("date")} ${it.at("amount").amount()}" },
            hydrated = counted.hydrated,
            reads = counted.reads,
        )
    }

    /**
     * A ledger holding the same March either way, and [outsideTheMonth] postings in years the
     * question never reaches.
     *
     * The two worlds differ in one respect only, which is what makes the comparison a measurement
     * of the month's cost rather than of two different questions.
     */
    private suspend fun ledgerOf(outsideTheMonth: Int): AgentWorld {
        val world = AgentWorld()

        world.account(ACCOUNT, "Nubank", isDefault = true)
        world.ledgerAccount(NOMINAL_EXPENSE, AccountEntity.Type.EXPENSE, "Despesas")
        world.category(id = 1, dimensionId = DIMENSION, name = "Mercado")

        MARCH.forEach { (date, cents) -> world.spend(date, cents) }
        repeat(outsideTheMonth) { world.spend(farFrom2026(it), cents = 1_000) }

        return world
    }

    /** Money leaving the account, classified — the shape of nearly every posting a user makes. */
    private suspend fun AgentWorld.spend(date: String, cents: Long) = posting(
        date,
        ACCOUNT posts -cents,
        (NOMINAL_EXPENSE posts cents).taggedWith(DIMENSION),
    )

    /** A date in the 2000s, so no amount of filling can wander into the month under test. */
    private fun farFrom2026(index: Int): String {
        val year = 2000 + index / (MONTHS_IN_A_YEAR * DAYS_USED)
        val month = 1 + (index / DAYS_USED) % MONTHS_IN_A_YEAR
        val day = 1 + index % DAYS_USED
        return "%04d-%02d-%02d".format(year, month, day)
    }

    /** March as the listing answers it: newest first, each posting as its date and its figure. */
    private fun marchNewestFirst(): List<String> = MARCH
        .map { (date, cents) -> "$date ${cents / 100.0}" }
        .reversed()

    private companion object {

        val json = Json { ignoreUnknownKeys = true }

        val ASK_FOR_MARCH = Json.parseToJsonElement("""{"month":"2026-03"}""").jsonObject

        const val ACCOUNT = 1L
        const val NOMINAL_EXPENSE = 100L
        const val DIMENSION = 1L

        /** The month under test: six postings, ascending in date and in amount. */
        val MARCH = listOf(
            "2026-03-02" to 10_000L,
            "2026-03-07" to 20_000L,
            "2026-03-12" to 30_000L,
            "2026-03-17" to 40_000L,
            "2026-03-22" to 50_000L,
            "2026-03-27" to 60_000L,
        )

        /** A year of a posting a month, and a ledger of a user who has been at it for years. */
        const val SMALL_LEDGER = 12
        const val LARGE_LEDGER = 3_000

        const val MONTHS_IN_A_YEAR = 12
        const val DAYS_USED = 28
    }
}

/**
 * The ledger's transaction port, counting what the listing makes it materialise.
 *
 * [hydrated] is the number the issue is about: `getAllTransactions` builds a `Transaction` per row
 * **and reads that row's entries in a query of its own**, so a posting handed back is a query and an
 * object graph, and the count of them is the cost of the call. [reads] is beside it because a single
 * call handing back the whole table and a call per row are the same cost and neither is a page's.
 *
 * Written out rather than delegated with `by` on purpose: a read added to the interface stops this
 * compiling, and whoever adds it has to say whether it hands transactions back. One that does and is
 * not counted here makes the measurement lie.
 */
private class CountingTransactions(
    private val delegate: ITransactionRepository,
) : ITransactionRepository {

    var reads = 0
        private set

    var hydrated = 0
        private set

    private fun <T> counting(transactions: List<T>): List<T> {
        reads++
        hydrated += transactions.size
        return transactions
    }

    override fun observeAllTransactions(): Flow<List<Transaction>> =
        delegate.observeAllTransactions().onEach { counting(it) }

    override fun observeTransactionsBy(
        date: LocalDate?,
        dimensionId: Long?,
        accountId: Long?,
    ): Flow<List<Transaction>> = delegate
        .observeTransactionsBy(date, dimensionId, accountId)
        .onEach { counting(it) }

    override fun observeTransactionById(id: Long): Flow<Transaction?> =
        delegate.observeTransactionById(id).onEach { counting(listOfNotNull(it)) }

    override suspend fun getAllTransactions(): List<Transaction> =
        counting(delegate.getAllTransactions())

    override suspend fun getTransactionsBetween(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<Transaction> = counting(delegate.getTransactionsBetween(startDate, endDate))

    override suspend fun getTransactionById(id: Long): Transaction? =
        delegate.getTransactionById(id).also { counting(listOfNotNull(it)) }

    /** Identities, and nothing hydrated: a read, but not a posting materialised. */
    override suspend fun getExistingTransactionIds(ids: Collection<Long>): Set<Long> {
        reads++
        return delegate.getExistingTransactionIds(ids)
    }

    // --- Writes, which a listing never reaches ------------------------------------------

    override suspend fun createTransaction(intent: TransactionIntent): Transaction =
        delegate.createTransaction(intent)

    override suspend fun createTransactions(intents: List<TransactionIntent>): List<Transaction> =
        delegate.createTransactions(intents)

    override suspend fun updateTransaction(
        id: Long,
        title: String?,
        date: LocalDate,
        leg: TransactionLeg,
        contra: ContraLeg?,
    ) = delegate.updateTransaction(id, title, date, leg, contra)

    override suspend fun deleteTransactionById(id: Long) = delegate.deleteTransactionById(id)

    override suspend fun deleteTransactionsByIds(ids: List<Long>) =
        delegate.deleteTransactionsByIds(ids)
}
