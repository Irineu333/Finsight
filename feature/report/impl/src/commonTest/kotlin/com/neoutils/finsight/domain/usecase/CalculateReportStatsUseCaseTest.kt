package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.ReportPerspective
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.LiabilityMonthFlows
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.DimensionFlows
import com.neoutils.finsight.domain.repository.ScopeStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The use case's only remaining logic is resolving a [ReportPerspective] into the ledger
 * accounts the report is "seen from" — the figures themselves come from the SQL aggregate
 * (pinned by ReportStatsQueryTest). This locks the resolution: selected accounts pass
 * through; an empty selection means every account, **including archived** (so their
 * history is not dropped); a card resolves to its LIABILITY account.
 */
class CalculateReportStatsUseCaseTest {

    private val start = LocalDate(2026, 3, 1)
    private val end = LocalDate(2026, 3, 31)

    private fun useCase(
        entry: CapturingEntryRepository,
        accounts: List<Account> = emptyList(),
        cards: List<CreditCard> = emptyList(),
    ) = CalculateReportStatsUseCase(
        entryRepository = entry,
        accountRepository = FakeAccountRepository(accounts),
        creditCardRepository = FakeCreditCardRepository(cards),
    )

    @Test
    fun `selected accounts pass through as the scope`() = runTest {
        val entry = CapturingEntryRepository()
        useCase(entry)(ReportPerspective.AccountPerspective(listOf(1, 2)), start, end)
        assertEquals(listOf(1L, 2L), entry.capturedScope)
    }

    @Test
    fun `an empty selection resolves to every account including archived`() = runTest {
        val entry = CapturingEntryRepository()
        val accounts = listOf(
            Account(id = 1, name = "open", type = AccountType.ASSET),
            Account(id = 2, name = "archived", type = AccountType.ASSET, isArchived = true),
        )
        useCase(entry, accounts = accounts)(ReportPerspective.AccountPerspective(emptyList()), start, end)
        assertEquals(listOf(1L, 2L), entry.capturedScope)
    }

    @Test
    fun `a card resolves to its liability account`() = runTest {
        val entry = CapturingEntryRepository()
        val card = CreditCard(id = 7, name = "Visa", limit = 1000.0, closingDay = 5, dueDay = 15, accountId = 200)
        useCase(entry, cards = listOf(card))(ReportPerspective.CreditCardPerspective(creditCardId = 7), start, end)
        assertEquals(listOf(200L), entry.capturedScope)
    }

    @Test
    fun `a card without a resolvable account yields an empty scope`() = runTest {
        val entry = CapturingEntryRepository()
        useCase(entry)(ReportPerspective.CreditCardPerspective(creditCardId = 99), start, end)
        assertEquals(emptyList(), entry.capturedScope)
    }
}

private class CapturingEntryRepository : IEntryRepository {
    var capturedScope: List<Long> = emptyList()
    override suspend fun scopeStats(scopeAccountIds: List<Long>, startDate: LocalDate, endDate: LocalDate): ScopeStats {
        capturedScope = scopeAccountIds
        return ScopeStats(0.0, 0.0, 0.0, 0.0)
    }

    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override fun observeLedgerChanges(): Flow<Unit> = throw NotImplementedError()
    override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double = throw NotImplementedError()
    override suspend fun hasEntries(accountId: Long): Boolean = throw NotImplementedError()
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = throw NotImplementedError()
    override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
    override suspend fun dimensionBalanceInMonth(month: YearMonth, dimensionId: Long): Double = throw NotImplementedError()
    override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows = throw NotImplementedError()
    override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int = throw NotImplementedError()
    override suspend fun dimensionOwed(dimensionId: Long): Double = throw NotImplementedError()
    override suspend fun dimensionFlows(dimensionId: Long): DimensionFlows = throw NotImplementedError()
    override suspend fun liabilityMonthFlows(month: YearMonth): LiabilityMonthFlows = throw NotImplementedError()
    override suspend fun assetMonthFlows(month: YearMonth): com.neoutils.finsight.domain.repository.AssetMonthFlows = throw NotImplementedError()
    override suspend fun netWorth(): Double = throw NotImplementedError()
    override suspend fun totalsByDimension(nominalType: AccountType, startDate: LocalDate, endDate: LocalDate, siblingAccountIds: List<Long>): Map<Long?, Double> = throw NotImplementedError()
    override suspend fun totalsByDimensionInScope(nominalType: AccountType, scopeDimensionIds: List<Long>): Map<Long?, Double> = throw NotImplementedError()
}

private class FakeAccountRepository(private val accounts: List<Account>) : IAccountRepository {
    override suspend fun getAllAccountsIncludingClosed(): List<Account> = accounts
    override fun observeAllAccounts(): Flow<List<Account>> = throw NotImplementedError()
    override suspend fun getAllAccounts(): List<Account> = throw NotImplementedError()
    override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = throw NotImplementedError()
    override suspend fun getAllLedgerAccounts(): List<Account> = throw NotImplementedError()
    override fun observeAllLedgerAccounts(): Flow<List<Account>> = throw NotImplementedError()
    override suspend fun getAccountById(accountId: Long): Account? = throw NotImplementedError()
    override fun observeAccountById(accountId: Long): Flow<Account?> = throw NotImplementedError()
    override suspend fun getDefaultAccount(): Account? = throw NotImplementedError()
    override fun observeDefaultAccount(): Flow<Account?> = throw NotImplementedError()
    override suspend fun getAccountCount(): Int = throw NotImplementedError()
    override suspend fun insert(account: Account): Long = throw NotImplementedError()
    override suspend fun update(account: Account) = throw NotImplementedError()
    override suspend fun delete(account: Account) = throw NotImplementedError()
}

private class FakeCreditCardRepository(private val cards: List<CreditCard>) : ICreditCardRepository {
    override suspend fun getCreditCardById(creditCardId: Long): CreditCard? = cards.firstOrNull { it.id == creditCardId }
    override fun observeAllCreditCards(): Flow<List<CreditCard>> = throw NotImplementedError()
    override suspend fun getAllCreditCards(): List<CreditCard> = throw NotImplementedError()
    override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> = throw NotImplementedError()
    override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> = throw NotImplementedError()
    override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> = throw NotImplementedError()
    override suspend fun insert(creditCard: CreditCard): Long = throw NotImplementedError()
    override suspend fun update(creditCard: CreditCard) = throw NotImplementedError()
    override suspend fun delete(creditCard: CreditCard) = throw NotImplementedError()
}
