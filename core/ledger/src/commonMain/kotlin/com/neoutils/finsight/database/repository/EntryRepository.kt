package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.CurrencyScoped
import com.neoutils.finsight.database.dao.DimensionCurrencyTotal
import com.neoutils.finsight.database.dao.EntryDao
import com.neoutils.finsight.database.dao.EntryWithAccount
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.database.mapper.toDomain
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.AssetMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.LiabilityMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.DimensionFlowsByCurrency
import com.neoutils.finsight.domain.repository.ScopeStatsByCurrency
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
        // No `currency` here: it is derived from the account, and the account is
        // hydrated whole from the join. The row's own column is what
        // `LedgerBalanceCheck` reads, not what the model carries.
        dimensionId = entry.dimensionId,
    )

    override fun observeLedgerChanges(): Flow<Unit> = entryDao.observeEntryCount().map { }

    override suspend fun hasEntries(accountId: Long): Boolean = entryDao.hasEntries(accountId)

    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean =
        entryDao.hasEntriesForDimension(dimensionId)

    override suspend fun accountBalanceUpTo(accountId: Long, target: LocalDate): Double =
        entryDao.balanceUpToDate(accountId, target.toString()) / CENTS_PER_UNIT

    // No account named means "every ASSET account" — the same read by nature, so the
    // accumulated balance has one path, not two.
    override suspend fun balanceUpToByCurrency(
        target: YearMonth,
        excludedAccountIds: Set<Long>,
    ): MoneyByCurrency =
        naturalBalanceUpToByCurrency(target, AccountType.ASSET, excludedAccountIds)

    override suspend fun naturalBalanceUpToByCurrency(
        target: YearMonth,
        type: AccountType,
        excludedAccountIds: Set<Long>,
    ): MoneyByCurrency = entryDao
        .balanceUpToMonthByType(type.name, target.toString(), excludedAccountIds)
        .toMoney { it.total }

    override suspend fun balance(accountId: Long): Double {
        return entryDao.balanceOf(accountId) / CENTS_PER_UNIT
    }

    override suspend fun dimensionBalanceInMonthByCurrency(
        month: YearMonth,
        dimensionId: Long,
    ): MoneyByCurrency =
        entryDao.dimensionBalanceInMonth(dimensionId, month.toString()).toMoney { it.total }

    override suspend fun accountFlows(
        month: YearMonth,
        accountId: Long,
        yieldDimensionId: Long?,
    ): AccountFlows {
        val totals = entryDao.accountPeriodTotals(accountId, month.toString(), yieldDimensionId)
        return AccountFlows(
            currency = totals.currency,
            income = totals.income / CENTS_PER_UNIT,
            yield = totals.yield / CENTS_PER_UNIT,
            expense = totals.expense / CENTS_PER_UNIT,
            adjustment = totals.adjustment / CENTS_PER_UNIT,
            settlement = totals.settlement / CENTS_PER_UNIT,
        )
    }

    override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int {
        return entryDao.dimensionEntryCountInMonth(dimensionId, month.toString())
    }

    override suspend fun dimensionOwedByCurrency(dimensionId: Long): MoneyByCurrency =
        // Liability entries are stored negative (credit); owed reads positive.
        entryDao.dimensionNaturalBalance(dimensionId).toMoney(negated = true) { it.total }

    override suspend fun dimensionFlowsByCurrency(
        dimensionId: Long,
    ): DimensionFlowsByCurrency {
        val rows = entryDao.dimensionPeriodTotals(dimensionId)
        return DimensionFlowsByCurrency(
            expense = rows.toMoney { it.expense },
            advancePayment = rows.toMoney { it.advancePayment },
            adjustment = rows.toMoney { it.adjustment },
        )
    }

    override suspend fun owedByDimensionByCurrency(
        dimensionIds: Collection<Long>,
    ): Map<Long, MoneyByCurrency> {
        if (dimensionIds.isEmpty()) return emptyMap()
        // Liability entries are stored negative (credit); owed reads positive.
        return entryDao.naturalBalanceByDimension(dimensionIds.distinct())
            .groupBy { it.dimensionId!! }
            .mapValues { (_, rows) -> rows.toMoney(negated = true) { it.total } }
    }

    override suspend fun flowsByDimensionByCurrency(
        dimensionIds: Collection<Long>,
    ): Map<Long, DimensionFlowsByCurrency> {
        if (dimensionIds.isEmpty()) return emptyMap()
        return entryDao.periodTotalsByDimension(dimensionIds.distinct())
            .groupBy { it.dimensionId }
            .mapValues { (_, rows) ->
                DimensionFlowsByCurrency(
                    expense = rows.toMoney { it.expense },
                    advancePayment = rows.toMoney { it.advancePayment },
                    adjustment = rows.toMoney { it.adjustment },
                )
            }
    }

    override suspend fun liabilityMonthFlowsByCurrency(
        month: YearMonth,
    ): LiabilityMonthFlowsByCurrency {
        val rows = entryDao.liabilityMonthTotals(month.toString())
        return LiabilityMonthFlowsByCurrency(
            expense = rows.toMoney { it.expense },
            payment = rows.toMoney { it.payment },
            adjustment = rows.toMoney { it.adjustment },
        )
    }

    override suspend fun assetMonthFlowsByCurrency(
        month: YearMonth,
        yieldDimensionId: Long?,
    ): AssetMonthFlowsByCurrency {
        val rows = entryDao.assetMonthTotals(month.toString(), yieldDimensionId)
        return AssetMonthFlowsByCurrency(
            income = rows.toMoney { it.income },
            yield = rows.toMoney { it.yield },
            expense = rows.toMoney { it.expense },
            adjustment = rows.toMoney { it.adjustment },
        )
    }

    override suspend fun netWorthByCurrency(): MoneyByCurrency =
        entryDao.netWorthCents().toMoney { it.total }

    override suspend fun totalsByDimensionByCurrency(
        nominalType: AccountType,
        startDate: LocalDate,
        endDate: LocalDate,
        siblingAccountIds: List<Long>,
    ): Map<Long?, MoneyByCurrency> {
        if (siblingAccountIds.isEmpty()) return emptyMap()
        return entryDao
            .totalsByDimensionWithSiblingLeg(nominalType.name, startDate, endDate, siblingAccountIds)
            .byDimension()
    }

    override suspend fun totalsByDimensionInMonthByCurrency(
        month: YearMonth,
        nominalType: AccountType,
    ): Map<Long?, MoneyByCurrency> = entryDao
        .totalsByDimensionInMonth(nominalType.name, month.toString())
        .byDimension()

    override suspend fun totalsByDimensionInScopeByCurrency(
        nominalType: AccountType,
        scopeDimensionIds: List<Long>,
    ): Map<Long?, MoneyByCurrency> {
        if (scopeDimensionIds.isEmpty()) return emptyMap()
        return entryDao
            .totalsByDimensionInScope(nominalType.name, scopeDimensionIds)
            .byDimension()
    }

    override suspend fun scopeStatsByCurrency(
        scopeAccountIds: List<Long>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): ScopeStatsByCurrency {
        if (scopeAccountIds.isEmpty()) return ScopeStatsByCurrency.zero
        val rows = entryDao.scopeStats(scopeAccountIds, startDate, endDate)
        return ScopeStatsByCurrency(
            income = rows.toMoney { it.income },
            expense = rows.toMoney { it.expense },
            balance = rows.toMoney { it.balance },
            openingBalance = rows.toMoney { it.openingBalance },
        )
    }
}

/**
 * One field of a grouped projection, lifted from cents to a figure per currency. The
 * row list carries several figures at once (income *and* expense *and* …), so each is
 * read out with its own selector rather than by mapping the list once.
 */
private inline fun <T : CurrencyScoped> List<T>.toMoney(
    negated: Boolean = false,
    value: (T) -> Long,
) = MoneyByCurrency.of(
    associate { it.currency to (if (negated) -value(it) else value(it)) / CENTS_PER_UNIT },
)

/**
 * The total of each dimension, per currency. The `null` key is the unclassified
 * group, exactly as in the scalar read it replaces.
 */
private fun List<DimensionCurrencyTotal>.byDimension(): Map<Long?, MoneyByCurrency> =
    groupBy { it.dimensionId }
        .mapValues { (_, rows) -> rows.toMoney { it.total } }
