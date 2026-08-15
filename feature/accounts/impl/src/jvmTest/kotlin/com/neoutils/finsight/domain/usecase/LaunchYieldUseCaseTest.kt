package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A yield is a launch: every call is a new transaction, and none of them recognises,
 * rewrites or accumulates onto anything — least of all the salary that lands on the
 * same account on the same day.
 */
class LaunchYieldUseCaseTest {

    private val date = LocalDate(2026, 7, 5)
    private val account = Account(id = 1, name = "Nubank", type = AccountType.ASSET, currency = "BRL", yieldsInterest = true)

    private fun useCase(store: RecordingTransactions, categories: YieldCategoryStore = YieldCategoryStore()) =
        LaunchYieldUseCase(
            transactionRepository = store,
            ensureYieldCategory = EnsureYieldCategoryUseCase(categories),
        )

    @Test
    fun `a yield is an income on the account against the nominal income account`() = runTest {
        val store = RecordingTransactions()

        assertTrue(useCase(store)(account, date, 12.40).isRight())

        val intent = store.created.single()
        assertEquals(date, intent.date)
        val leg = intent.legs.single()
        assertEquals(TransactionType.INCOME, leg.type)
        assertEquals(12.40, leg.amount)
        assertEquals(account.id, leg.accountId)
        assertEquals(AccountType.INCOME, intent.contra?.nature)
    }

    @Test
    fun `the contra leg carries the yield category dimension`() = runTest {
        val store = RecordingTransactions()
        val categories = YieldCategoryStore()

        useCase(store, categories)(account, date, 12.40)

        val category = categories.getCategoryBySystemKey("yield")!!
        assertEquals(category.dimensionId, store.created.single().contra?.dimensionId)
        // The account leg carries none: the analytic axis lands on the nominal side.
        assertEquals(null, store.created.single().legs.single().dimensionId)
    }

    @Test
    fun `no equity leg is ever created`() = runTest {
        val store = RecordingTransactions()

        useCase(store)(account, date, 12.40)

        // A yield is money that came in, not the correction of a wrong balance. An
        // EQUITY counterpart would file it as reconciliation and hide it from the
        // report — which is exactly the state this change exists to leave behind.
        assertTrue(store.created.none { it.contra?.nature == AccountType.EQUITY })
        assertTrue(store.created.none { it.legs.any { leg -> leg.type == TransactionType.ADJUSTMENT } })
    }

    @Test
    fun `two yields on the same date are two transactions`() = runTest {
        val store = RecordingTransactions()
        val launch = useCase(store)

        launch(account, date, 12.40)
        launch(account, date, 8.00)

        assertEquals(2, store.created.size)
        assertEquals(listOf(12.40, 8.00), store.created.map { it.legs.single().amount })
        assertTrue(store.updated.isEmpty(), "a launch recognises nothing and rewrites nothing")
        assertTrue(store.deleted.isEmpty())
    }

    @Test
    fun `an ordinary income on the same date is left alone`() = runTest {
        val store = RecordingTransactions()
        val salary = store.createTransaction(
            TransactionIntent(
                title = "Salário",
                date = date,
                legs = listOf(TransactionLeg(TransactionType.INCOME, 5_000.0, account.id)),
                contra = ContraLeg(AccountType.INCOME, dimensionId = 77),
            )
        )

        useCase(store)(account, date, 12.40)

        assertEquals(2, store.created.size)
        assertTrue(store.updated.isEmpty())
        assertTrue(store.deleted.isEmpty())
        assertEquals(5_000.0, store.created.first { it.title == "Salário" }.legs.single().amount)
        assertEquals(1L, salary.id)
    }

    @Test
    fun `an archived yield category still receives the launch`() = runTest {
        val store = RecordingTransactions()
        val categories = YieldCategoryStore()
        val launch = useCase(store, categories)

        launch(account, date, 12.40)
        val category = categories.getCategoryBySystemKey("yield")!!
        categories.archive(category.id)

        assertTrue(launch(account, date, 8.00).isRight())
        assertEquals(2, store.created.size)
        assertTrue(store.created.all { it.contra?.dimensionId == category.dimensionId })
    }
}

/** Records the intents written, so a launch can be told from an adjustment. */
private class RecordingTransactions : ITransactionRepository {

    val created = mutableListOf<TransactionIntent>()
    val updated = mutableListOf<Long>()
    val deleted = mutableListOf<Long>()

    override suspend fun createTransaction(intent: TransactionIntent): Transaction {
        created += intent
        return Transaction(id = created.size.toLong(), title = intent.title, date = intent.date, entries = emptyList())
    }

    override suspend fun createTransactions(intents: List<TransactionIntent>): List<Transaction> =
        intents.map { createTransaction(it) }

    override suspend fun updateTransaction(
        id: Long,
        title: String?,
        date: LocalDate,
        leg: TransactionLeg,
        contra: ContraLeg?,
    ) { updated += id }

    override suspend fun deleteTransactionById(id: Long) { deleted += id }
    override suspend fun deleteTransactionsByIds(ids: List<Long>) { deleted += ids }
    override suspend fun getTransactionsBy(
        startDate: LocalDate?,
        endDate: LocalDate?,
        dimensionId: Long?,
        accountId: Long?,
    ): List<Transaction> = throw NotImplementedError()

    override fun observeTransactionsBy(date: LocalDate?, dimensionId: Long?, accountId: Long?): Flow<List<Transaction>> =
        flowOf(emptyList())
    override fun observeAllTransactions(): Flow<List<Transaction>> = throw NotImplementedError()
    override fun observeTransactionById(id: Long): Flow<Transaction?> = throw NotImplementedError()
    override suspend fun getAllTransactions(): List<Transaction> = throw NotImplementedError()
    override suspend fun getTransactionById(id: Long): Transaction? = throw NotImplementedError()
}
