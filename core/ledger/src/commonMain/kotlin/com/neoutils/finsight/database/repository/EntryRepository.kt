package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.CurrencyGrouped
import com.neoutils.finsight.database.dao.CurrencyTotal
import com.neoutils.finsight.database.dao.EntryDao
import com.neoutils.finsight.database.dao.EntryWithAccount
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.database.mapper.toDomain
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CurrencyBalance
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.repository.AccountBalance
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.AssetMonthFlows
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.LiabilityMonthFlows
import com.neoutils.finsight.domain.repository.DimensionFlows
import com.neoutils.finsight.domain.repository.ScopeStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

private const val CENTS_PER_UNIT = 100.0

class EntryRepository(
    private val entryDao: EntryDao,
) : IEntryRepository {

    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> =
        entryDao.getEntriesWithAccountByTransactionId(transactionId).map { it.toDomain() }

    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> =
        entryDao.observeEntriesWithAccountByTransactionId(transactionId)
            .map { rows -> rows.map { it.toDomain() } }

    private fun EntryWithAccount.toDomain() = Entry(
        id = entry.id,
        transactionId = entry.transactionId,
        account = Account(
            id = account.id,
            name = account.name,
            type = account.type.toDomain(),
            currency = account.currency,
            iconKey = account.iconKey,
            isDefault = account.isDefault,
            createdAt = account.createdAt,
            // Closure travels with the account: a leg that drops it reports every
            // archived account as open, and the rules derived from it go quiet.
            isArchived = account.isArchived,
        ),
        amount = entry.amount,
        currency = entry.currency,
        dimensionId = entry.dimensionId,
    )

    override fun observeLedgerChanges(): Flow<Unit> = entryDao.observeEntryCount().map { }

    override suspend fun hasEntries(accountId: Long): Boolean = entryDao.hasEntries(accountId)

    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean =
        entryDao.hasEntriesForDimension(dimensionId)

    override suspend fun balanceUpTo(target: YearMonth, accountId: Long): AccountBalance =
        entryDao.balanceUpToMonth(accountId, target.toString())
            .orNoSuchAccount(accountId)
            .toAccountBalance()

    override suspend fun naturalBalanceUpTo(target: YearMonth, type: AccountType): CurrencyBalance =
        entryDao.balanceUpToMonthByType(type.name, target.toString()).toCurrencyBalance()

    override suspend fun balance(accountId: Long): AccountBalance =
        entryDao.balanceOf(accountId).orNoSuchAccount(accountId).toAccountBalance()

    override suspend fun dimensionBalanceInMonth(
        month: YearMonth,
        dimensionId: Long,
    ): CurrencyBalance =
        entryDao.dimensionBalanceInMonth(dimensionId, month.toString()).toCurrencyBalance()

    override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows {
        val totals = entryDao.accountPeriodTotals(accountId, month.toString())
            .orNoSuchAccount(accountId)
        return AccountFlows(
            currency = totals.currency,
            income = totals.income / CENTS_PER_UNIT,
            expense = totals.expense / CENTS_PER_UNIT,
            adjustment = totals.adjustment / CENTS_PER_UNIT,
            settlement = totals.settlement / CENTS_PER_UNIT,
        )
    }

    override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int {
        return entryDao.dimensionEntryCountInMonth(dimensionId, month.toString())
    }

    override suspend fun dimensionOwed(dimensionId: Long): CurrencyBalance {
        // Liability entries are stored negative (credit); owed reads positive.
        return entryDao.dimensionNaturalBalance(dimensionId).toCurrencyBalance(negated = true)
    }

    override suspend fun dimensionFlows(dimensionId: Long): DimensionFlows {
        val rows = entryDao.dimensionPeriodTotals(dimensionId)
        return DimensionFlows(
            expense = rows.balanceOf { it.expense },
            advancePayment = rows.balanceOf { it.advancePayment },
            adjustment = rows.balanceOf { it.adjustment },
        )
    }

    override suspend fun owedByDimension(dimensionIds: Collection<Long>): Map<Long, CurrencyBalance> {
        if (dimensionIds.isEmpty()) return emptyMap()
        // Liability entries are stored negative (credit); owed reads positive.
        return entryDao.naturalBalanceByDimension(dimensionIds.distinct())
            .groupBy { it.dimensionId!! }
            .mapValues { (_, rows) -> rows.balanceOf { -it.total } }
    }

    override suspend fun flowsByDimension(dimensionIds: Collection<Long>): Map<Long, DimensionFlows> {
        if (dimensionIds.isEmpty()) return emptyMap()
        return entryDao.periodTotalsByDimension(dimensionIds.distinct())
            .groupBy { it.dimensionId }
            .mapValues { (_, rows) ->
                DimensionFlows(
                    expense = rows.balanceOf { it.expense },
                    advancePayment = rows.balanceOf { it.advancePayment },
                    adjustment = rows.balanceOf { it.adjustment },
                )
            }
    }

    override suspend fun liabilityMonthFlows(month: YearMonth): LiabilityMonthFlows {
        val rows = entryDao.liabilityMonthTotals(month.toString())
        return LiabilityMonthFlows(
            expense = rows.balanceOf { it.expense },
            payment = rows.balanceOf { it.payment },
            adjustment = rows.balanceOf { it.adjustment },
        )
    }

    override suspend fun assetMonthFlows(month: YearMonth): AssetMonthFlows {
        val rows = entryDao.assetMonthTotals(month.toString())
        return AssetMonthFlows(
            income = rows.balanceOf { it.income },
            expense = rows.balanceOf { it.expense },
            adjustment = rows.balanceOf { it.adjustment },
        )
    }

    override suspend fun totalsByDimension(
        nominalType: AccountType,
        startDate: LocalDate,
        endDate: LocalDate,
        siblingAccountIds: List<Long>,
    ): Map<Long?, CurrencyBalance> {
        if (siblingAccountIds.isEmpty()) return emptyMap()
        return entryDao
            .totalsByDimensionWithSiblingLeg(nominalType.name, startDate, endDate, siblingAccountIds)
            .groupBy { it.dimensionId }
            .mapValues { (_, rows) -> rows.balanceOf { it.total } }
    }

    override suspend fun totalsByDimensionInScope(
        nominalType: AccountType,
        scopeDimensionIds: List<Long>,
    ): Map<Long?, CurrencyBalance> {
        if (scopeDimensionIds.isEmpty()) return emptyMap()
        return entryDao
            .totalsByDimensionInScope(nominalType.name, scopeDimensionIds)
            .groupBy { it.dimensionId }
            .mapValues { (_, rows) -> rows.balanceOf { it.total } }
    }

    override suspend fun scopeStats(
        scopeAccountIds: List<Long>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): ScopeStats {
        if (scopeAccountIds.isEmpty()) return ScopeStats()
        val rows = entryDao.scopeStats(scopeAccountIds, startDate, endDate)
        return ScopeStats(
            income = rows.balanceOf { it.income },
            expense = rows.balanceOf { it.expense },
            balance = rows.balanceOf { it.balance },
            openingBalance = rows.balanceOf { it.openingBalance },
        )
    }

    /**
     * The one place cents become the major unit and grouped rows become a per-currency
     * figure. Every grouped read goes through it, so no aggregate invents its own way of
     * keying by currency and none of them can accidentally add two currencies up.
     */
    private fun <T : CurrencyGrouped> List<T>.balanceOf(value: (T) -> Long) =
        CurrencyBalance.of(associate { it.currency to value(it) / CENTS_PER_UNIT })

    private fun List<CurrencyTotal>.toCurrencyBalance(negated: Boolean = false) =
        balanceOf { if (negated) -it.total else it.total }

    private fun CurrencyTotal.toAccountBalance() =
        AccountBalance(currency = currency, amount = total / CENTS_PER_UNIT)

    /**
     * A figure scoped to one account has no answer when that account is not in the chart —
     * and a `0` would be a lie in a currency nobody named. Every production caller reaches
     * these reads holding the account itself, so this cannot be reached by asking.
     */
    private fun <T> T?.orNoSuchAccount(accountId: Long): T =
        requireNotNull(this) { "No account $accountId in the chart of accounts" }
}
