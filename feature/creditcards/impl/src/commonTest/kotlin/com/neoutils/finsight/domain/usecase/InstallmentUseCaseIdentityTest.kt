package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.database.dao.InstallmentDao
import com.neoutils.finsight.database.entity.InstallmentEntity
import com.neoutils.finsight.domain.error.InstallmentError
import com.neoutils.finsight.domain.exception.InstallmentException
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The installment is named by its **id**, and both operations resolve it when they run:
 * removing one takes with it whatever points at the installment at that moment, not
 * whatever a screen listed earlier, and editing one writes onto a row that still exists.
 */
class InstallmentUseCaseIdentityTest {

    private val installment = Installment(id = 7, count = 3, totalAmount = 300.0)

    private fun deleteUseCase(
        store: InstallmentStore,
        transactions: RecordingTransactionDeleter,
        dao: FakeInstallmentDao,
    ) = DeleteInstallmentUseCaseImpl(
        transactionRepository = transactions,
        installmentRepository = store,
        installmentDao = dao,
    )

    @Test
    fun `deleting an installment that does not exist is refused and nothing is removed`() = runTest {
        val store = InstallmentStore(installment)
        val transactions = RecordingTransactionDeleter()
        val dao = FakeInstallmentDao(mapOf(installment.id to listOf(1L, 2L, 3L)))

        val error = assertIs<InstallmentException>(
            deleteUseCase(store, transactions, dao)(404L).leftOrNull()
        )

        assertEquals(InstallmentError.NotFound, error.error)
        assertTrue(transactions.deleted.isEmpty(), "no transaction may be removed")
        assertTrue(store.deleted.isEmpty(), "and the installment stays")
    }

    @Test
    fun `deleting takes the transactions that name the installment now`() = runTest {
        val store = InstallmentStore(installment)
        val transactions = RecordingTransactionDeleter()
        val dao = FakeInstallmentDao(mapOf(installment.id to listOf(1L, 2L, 3L)))

        val result = deleteUseCase(store, transactions, dao)(installment.id)

        assertTrue(result.isRight())
        assertEquals(listOf(1L, 2L, 3L), transactions.deleted)
        assertEquals(listOf(installment.id), store.deleted)
    }

    @Test
    fun `deleting by id and by installment are the same operation`() = runTest {
        val byIdStore = InstallmentStore(installment)
        val byIdTransactions = RecordingTransactionDeleter()
        val byInstallmentStore = InstallmentStore(installment)
        val byInstallmentTransactions = RecordingTransactionDeleter()
        val ids = mapOf(installment.id to listOf(1L, 2L))

        val fromId = deleteUseCase(
            byIdStore, byIdTransactions, FakeInstallmentDao(ids),
        )(installment.id)
        val fromInstallment = deleteUseCase(
            byInstallmentStore, byInstallmentTransactions, FakeInstallmentDao(ids),
        )(installment)

        assertEquals(fromId.isRight(), fromInstallment.isRight())
        assertEquals(byIdTransactions.deleted, byInstallmentTransactions.deleted)
        assertEquals(byIdStore.deleted, byInstallmentStore.deleted)
    }

    @Test
    fun `updating an installment that does not exist is refused and nothing is written`() = runTest {
        val store = InstallmentStore(installment)

        val error = assertIs<InstallmentException>(
            UpdateInstallmentUseCaseImpl(store)(
                installmentId = 404L,
                count = 4,
                totalAmount = 400.0,
            ).leftOrNull()
        )

        assertEquals(InstallmentError.NotFound, error.error)
        assertTrue(store.updates.isEmpty(), "nothing may be written")
    }

    @Test
    fun `updating answers the installment as stored, identity included`() = runTest {
        val store = InstallmentStore(installment)

        val updated = UpdateInstallmentUseCaseImpl(store)(
            installmentId = installment.id,
            count = 4,
            totalAmount = 400.0,
        ).getOrNull()

        assertEquals(Installment(id = 7, count = 4, totalAmount = 400.0), updated)
        assertEquals(listOf(Triple(7L, 4, 400.0)), store.updates)
    }

    @Test
    fun `updating by id and by installment are the same operation`() = runTest {
        val byId = InstallmentStore(installment)
        val byInstallment = InstallmentStore(installment)

        val fromId = UpdateInstallmentUseCaseImpl(byId)(installment.id, 4, 400.0)
        val fromInstallment = UpdateInstallmentUseCaseImpl(byInstallment)(installment, 4, 400.0)

        assertEquals(fromId.getOrNull(), fromInstallment.getOrNull())
        assertEquals(byId.updates, byInstallment.updates)
    }

    @Test
    fun `an installment of no shares is refused, and so is a total of nothing`() = runTest {
        val store = InstallmentStore(installment)

        val noShares = assertIs<InstallmentException>(
            UpdateInstallmentUseCaseImpl(store)(installment.id, 0, 400.0).leftOrNull()
        )
        val noTotal = assertIs<InstallmentException>(
            UpdateInstallmentUseCaseImpl(store)(installment.id, 4, 0.0).leftOrNull()
        )

        assertEquals(InstallmentError.NonPositiveCount, noShares.error)
        assertEquals(InstallmentError.NonPositiveTotal, noTotal.error)
        assertTrue(store.updates.isEmpty(), "neither may be written")
    }
}

private class InstallmentStore(private vararg val rows: Installment) : IInstallmentRepository {
    val deleted = mutableListOf<Long>()
    val updates = mutableListOf<Triple<Long, Int, Double>>()

    override suspend fun getInstallmentById(id: Long): Installment? =
        rows.firstOrNull { it.id == id }

    override suspend fun getAllInstallments(): List<Installment> = rows.toList()
    override fun observeAllInstallments(): Flow<List<Installment>> = flowOf(rows.toList())
    override suspend fun createInstallment(count: Int, totalAmount: Double): Long =
        throw NotImplementedError()

    override suspend fun updateInstallment(id: Long, count: Int, totalAmount: Double) {
        updates += Triple(id, count, totalAmount)
    }

    override suspend fun deleteInstallmentById(id: Long) { deleted += id }
}

/** The transactions that name each installment, as the facade's own DAO answers them. */
private class FakeInstallmentDao(
    private val transactionsByInstallment: Map<Long, List<Long>>,
) : InstallmentDao {
    override suspend fun transactionIds(installmentId: Long): List<Long> =
        transactionsByInstallment[installmentId].orEmpty()

    override suspend fun countTransactions(installmentId: Long): Int =
        transactionIds(installmentId).size

    override suspend fun detachTransactions(installmentId: Long) = throw NotImplementedError()
    override suspend fun insert(installment: InstallmentEntity): Long = throw NotImplementedError()
    override suspend fun getById(id: Long): InstallmentEntity? = throw NotImplementedError()
    override fun observeAll(): Flow<List<InstallmentEntity>> = throw NotImplementedError()
    override suspend fun getAll(): List<InstallmentEntity> = throw NotImplementedError()
    override suspend fun deleteById(id: Long) = throw NotImplementedError()
    override suspend fun updateById(id: Long, count: Int, totalAmount: Double) =
        throw NotImplementedError()
}

private class RecordingTransactionDeleter : ITransactionRepository {
    val deleted = mutableListOf<Long>()

    override suspend fun deleteTransactionsByIds(ids: List<Long>) { deleted += ids }
    override suspend fun deleteTransactionById(id: Long) { deleted += id }
    override fun observeAllTransactions(): Flow<List<Transaction>> = throw NotImplementedError()
    override fun observeTransactionsBy(
        date: LocalDate?,
        dimensionId: Long?,
        accountId: Long?,
    ): Flow<List<Transaction>> = throw NotImplementedError()
    override fun observeTransactionById(id: Long): Flow<Transaction?> = throw NotImplementedError()
    override suspend fun getAllTransactions(): List<Transaction> = throw NotImplementedError()
    override suspend fun getTransactionById(id: Long): Transaction? = throw NotImplementedError()
    override suspend fun getExistingTransactionIds(ids: Collection<Long>): Set<Long> = throw NotImplementedError()
    override suspend fun createTransaction(intent: TransactionIntent): Transaction =
        throw NotImplementedError()
    override suspend fun createTransactions(intents: List<TransactionIntent>): List<Transaction> =
        throw NotImplementedError()
    override suspend fun updateTransaction(
        id: Long,
        title: String?,
        date: LocalDate,
        leg: TransactionLeg,
        contra: ContraLeg?,
    ) = throw NotImplementedError()
}
