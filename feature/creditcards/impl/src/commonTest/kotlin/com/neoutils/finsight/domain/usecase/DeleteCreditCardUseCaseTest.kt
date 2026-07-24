package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.AssetMonthFlows
import com.neoutils.finsight.domain.repository.DimensionFlows
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.repository.LiabilityMonthFlows
import com.neoutils.finsight.domain.repository.ScopeStats
import com.neoutils.finsight.domain.model.Recurring
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    override suspend fun insert(recurring: Recurring) = throw NotImplementedError()
    override suspend fun update(recurring: Recurring) = throw NotImplementedError()
    override suspend fun delete(recurring: Recurring) = throw NotImplementedError()
}

private class StubEntryRepository(private val hasEntries: Boolean) : IEntryRepository {
    override suspend fun hasEntries(accountId: Long): Boolean = hasEntries
    override suspend fun dimensionOwed(dimensionId: Long): Double = throw NotImplementedError()
    override suspend fun dimensionFlows(dimensionId: Long): DimensionFlows = throw NotImplementedError()
    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double = throw NotImplementedError()
    override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
    override suspend fun dimensionBalanceInMonth(month: YearMonth, dimensionId: Long): Double = throw NotImplementedError()
    override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows = throw NotImplementedError()
    override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int = throw NotImplementedError()
    override suspend fun liabilityMonthFlows(month: YearMonth): LiabilityMonthFlows = throw NotImplementedError()
    override suspend fun assetMonthFlows(month: YearMonth): AssetMonthFlows = throw NotImplementedError()
    override suspend fun netWorth(): Double = throw NotImplementedError()
    override suspend fun totalsByDimension(nominalType: AccountType, startDate: LocalDate, endDate: LocalDate, siblingAccountIds: List<Long>): Map<Long?, Double> = throw NotImplementedError()
    override suspend fun totalsByDimensionInScope(nominalType: AccountType, scopeDimensionIds: List<Long>): Map<Long?, Double> = throw NotImplementedError()
    override suspend fun scopeStats(scopeAccountIds: List<Long>, startDate: LocalDate, endDate: LocalDate): ScopeStats = throw NotImplementedError()
}
