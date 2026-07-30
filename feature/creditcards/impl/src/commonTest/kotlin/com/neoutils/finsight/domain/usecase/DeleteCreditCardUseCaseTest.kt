package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.AssetMonthFlows
import com.neoutils.finsight.domain.repository.DimensionFlows
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.repository.LiabilityMonthFlows
import com.neoutils.finsight.domain.repository.ScopeStats
import com.neoutils.finsight.test.StubEntryRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * A card with movement — or a recurring still pointing at it — is refused with a typed
 * error, never quietly closed. Mirrors `RetireAccountGuardsTest` for the card facade,
 * the guard `DeleteCreditCardUseCase` owns beside `ArchiveCreditCardUseCase`.
 */
class DeleteCreditCardUseCaseTest {

    private fun useCase(
        hasEntries: Boolean = false,
        hasRecurring: Boolean = false,
        repository: RecordingCreditCardRepository = RecordingCreditCardRepository(),
    ) = DeleteCreditCardUseCase(
        creditCardRepository = repository,
        entryRepository = StubEntryRepository(hasEntries),
        recurringRepository = FakeRecurringRepository(hasRecurring),
    )

    private val card = CreditCard(
        currency = "BRL",
        id = 1L, name = "Card", limit = 1000.0, closingDay = 10, dueDay = 20, accountId = 42L,
    )

    @Test
    fun `a card with transactions is refused and not deleted`() = runTest {
        val repository = RecordingCreditCardRepository()

        val result = useCase(hasEntries = true, repository = repository)(card)

        assertEquals(AccountError.HAS_TRANSACTIONS, (result.leftOrNull() as AccountException).error)
        assertTrue(repository.deleted.isEmpty(), "nothing may be removed")
    }

    @Test
    fun `a card a recurring still uses is refused and not deleted`() = runTest {
        val repository = RecordingCreditCardRepository()

        val result = useCase(hasRecurring = true, repository = repository)(card)

        assertEquals(AccountError.HAS_RECURRING, (result.leftOrNull() as AccountException).error)
        assertTrue(repository.deleted.isEmpty(), "nothing may be removed")
    }

    @Test
    fun `a card that never moved is deleted`() = runTest {
        val repository = RecordingCreditCardRepository()

        val result = useCase(repository = repository)(card)

        assertTrue(result.isRight())
        assertEquals(listOf(card), repository.deleted)
    }
}

private class RecordingCreditCardRepository : ICreditCardRepository {
    val deleted = mutableListOf<CreditCard>()
    override suspend fun delete(creditCard: CreditCard) { deleted += creditCard }
    override fun observeAllCreditCards(): Flow<List<CreditCard>> = throw NotImplementedError()
    override suspend fun getAllCreditCards(): List<CreditCard> = throw NotImplementedError()
    override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> = throw NotImplementedError()
    override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> = throw NotImplementedError()
    override suspend fun getCreditCardById(creditCardId: Long): CreditCard? = throw NotImplementedError()
    override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> = throw NotImplementedError()
    override suspend fun insert(creditCard: CreditCard): Long = throw NotImplementedError()
    override suspend fun update(creditCard: CreditCard) = throw NotImplementedError()
    override suspend fun unarchive(accountId: Long) = throw NotImplementedError()
}

private class FakeRecurringRepository(private val hasRecurring: Boolean) : IRecurringRepository {
    override suspend fun hasRecurringForCreditCard(creditCardId: Long): Boolean = hasRecurring
    override fun observeAllRecurring(): Flow<List<Recurring>> = flowOf(emptyList())
    override fun observeRecurringById(id: Long): Flow<Recurring?> = throw NotImplementedError()
    override suspend fun getRecurringById(id: Long): Recurring? = null
    override suspend fun hasRecurringForAccount(accountId: Long): Boolean = false
    override suspend fun hasRecurringForCategory(categoryId: Long): Boolean = false
    override suspend fun hasTransactionForRecurring(recurringId: Long): Boolean = false
    override suspend fun insert(recurring: Recurring) = throw NotImplementedError()
    override suspend fun update(recurring: Recurring) = throw NotImplementedError()
    override suspend fun delete(recurring: Recurring) = throw NotImplementedError()
}

private class StubEntryRepository(private val hasEntries: Boolean) : StubEntryRepository() {
    override suspend fun hasEntries(accountId: Long): Boolean = hasEntries
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
}
