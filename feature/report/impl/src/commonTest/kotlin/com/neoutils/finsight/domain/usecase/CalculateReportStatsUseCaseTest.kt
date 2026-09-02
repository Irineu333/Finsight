package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.ReportPerspective
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.repository.AssetMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.DimensionFlowsByCurrency
import com.neoutils.finsight.domain.repository.LiabilityMonthFlowsByCurrency

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
    ) = CalculateReportStatsUseCaseImpl(
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
            Account(id = 1, name = "open", type = AccountType.ASSET, currency = "BRL"),
            Account(id = 2, name = "archived", type = AccountType.ASSET, isArchived = true, currency = "BRL"),
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
    override suspend fun scopeStatsByCurrency(
        scopeAccountIds: List<Long>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): com.neoutils.finsight.domain.repository.ScopeStatsByCurrency {
        capturedScope = scopeAccountIds
        return com.neoutils.finsight.domain.repository.ScopeStatsByCurrency.zero
    }

    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override fun observeLedgerChanges(): Flow<Unit> = throw NotImplementedError()
    override suspend fun hasEntries(accountId: Long): Boolean = throw NotImplementedError()
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = throw NotImplementedError()
    override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
    override suspend fun accountFlows(month: YearMonth, accountId: Long, yieldDimensionId: Long?): AccountFlows = throw NotImplementedError()

    override suspend fun accountBalanceUpTo(accountId: Long, target: LocalDate): Double = throw NotImplementedError()
    override suspend fun balanceUpToByCurrency(target: YearMonth, excludedAccountIds: Set<Long>): MoneyByCurrency = throw NotImplementedError()
    override suspend fun naturalBalanceUpToByCurrency(target: YearMonth, type: AccountType, excludedAccountIds: Set<Long>): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionMonthlySeriesByCurrency(dimensionId: Long, upTo: YearMonth): Map<YearMonth, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun dimensionBalanceInMonthByCurrency(month: YearMonth, dimensionId: Long): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionOwedByCurrency(dimensionId: Long): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionFlowsByCurrency(dimensionId: Long): DimensionFlowsByCurrency = throw NotImplementedError()
    override suspend fun owedByDimensionByCurrency(dimensionIds: Collection<Long>): Map<Long, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun flowsByDimensionByCurrency(dimensionIds: Collection<Long>): Map<Long, DimensionFlowsByCurrency> = throw NotImplementedError()
    override suspend fun liabilityMonthFlowsByCurrency(month: YearMonth): LiabilityMonthFlowsByCurrency = throw NotImplementedError()
    override suspend fun netWorthByCurrency(): MoneyByCurrency = throw NotImplementedError()
    override suspend fun assetMonthFlowsByCurrency(month: YearMonth, yieldDimensionId: Long?): AssetMonthFlowsByCurrency = throw NotImplementedError()
    override suspend fun totalsByDimensionByCurrency(
        nominalType: AccountType,
        startDate: LocalDate,
        endDate: LocalDate,
        siblingAccountIds: List<Long>,
    ): Map<Long?, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun totalsByDimensionInMonthByCurrency(
        month: YearMonth,
        nominalType: AccountType,
    ): Map<Long?, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun totalsByDimensionInScopeByCurrency(
        nominalType: AccountType,
        scopeDimensionIds: List<Long>,
    ): Map<Long?, MoneyByCurrency> = throw NotImplementedError()
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
    override suspend fun hasYieldingAccount(): Boolean = false
    override suspend fun getAccountCount(): Int = throw NotImplementedError()
    override suspend fun insert(account: Account): Long = throw NotImplementedError()
    override suspend fun update(account: Account) = throw NotImplementedError()
    override suspend fun delete(account: Account) = throw NotImplementedError()
    override suspend fun reopen(accountId: Long) = throw NotImplementedError()
}

private class FakeCreditCardRepository(private val cards: List<CreditCard>) : ICreditCardRepository {
    override suspend fun getCreditCardById(creditCardId: Long): CreditCard? = cards.firstOrNull { it.id == creditCardId }
    override fun observeAllCreditCards(): Flow<List<CreditCard>> = throw NotImplementedError()
    override suspend fun getAllCreditCards(): List<CreditCard> = throw NotImplementedError()
    override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> = throw NotImplementedError()
    override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> = throw NotImplementedError()
    override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> = throw NotImplementedError()
    override suspend fun insert(creditCard: CreditCard, currency: String): Long = throw NotImplementedError()
    override suspend fun update(creditCard: CreditCard) = throw NotImplementedError()
    override suspend fun delete(creditCard: CreditCard) = throw NotImplementedError()
    override suspend fun unarchive(accountId: Long) = throw NotImplementedError()
    override suspend fun currencyForNewCard(): String = throw NotImplementedError()
}
