@file:Suppress("DEPRECATION")

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
import com.neoutils.finsight.domain.repository.AssetMonthFlows
import com.neoutils.finsight.domain.repository.AssetMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.LiabilityMonthFlows
import com.neoutils.finsight.domain.repository.LiabilityMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.DimensionFlows
import com.neoutils.finsight.domain.repository.DimensionFlowsByCurrency
import com.neoutils.finsight.domain.repository.ScopeStats
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

    override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double {
        // No account named means "every ASSET account" — the same read by nature,
        // so the accumulated balance has one path, not two.
        if (accountId == null) return naturalBalanceUpTo(target, AccountType.ASSET)
        return accountBalanceUpTo(accountId, target)
    }

    override suspend fun accountBalanceUpTo(accountId: Long, target: YearMonth): Double =
        entryDao.balanceUpToMonth(accountId, target.toString()) / CENTS_PER_UNIT

    override suspend fun balanceUpToByCurrency(target: YearMonth): MoneyByCurrency =
        naturalBalanceUpToByCurrency(target, AccountType.ASSET)

    override suspend fun naturalBalanceUpTo(target: YearMonth, type: AccountType): Double =
        naturalBalanceUpToByCurrency(target, type).soleValue()

    override suspend fun naturalBalanceUpToByCurrency(
        target: YearMonth,
        type: AccountType,
    ): MoneyByCurrency = entryDao
        .balanceUpToMonthByType(type.name, target.toString())
        .toMoney { it.total }

    override suspend fun balance(accountId: Long): Double {
        return entryDao.balanceOf(accountId) / CENTS_PER_UNIT
    }

    override suspend fun dimensionBalanceInMonth(month: YearMonth, dimensionId: Long): Double =
        dimensionBalanceInMonthByCurrency(month, dimensionId).soleValue()

    override suspend fun dimensionBalanceInMonthByCurrency(
        month: YearMonth,
        dimensionId: Long,
    ): MoneyByCurrency =
        entryDao.dimensionBalanceInMonth(dimensionId, month.toString()).toMoney { it.total }

    override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows {
        val totals = entryDao.accountPeriodTotals(accountId, month.toString())
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

    override suspend fun dimensionOwed(dimensionId: Long): Double =
        dimensionOwedByCurrency(dimensionId).soleValue()

    override suspend fun dimensionOwedByCurrency(dimensionId: Long): MoneyByCurrency =
        // Liability entries are stored negative (credit); owed reads positive.
        entryDao.dimensionNaturalBalance(dimensionId).toMoney(negated = true) { it.total }

    override suspend fun dimensionFlows(dimensionId: Long): DimensionFlows {
        val flows = dimensionFlowsByCurrency(dimensionId)
        return DimensionFlows(
            expense = flows.expense.soleValue(),
            advancePayment = flows.advancePayment.soleValue(),
            adjustment = flows.adjustment.soleValue(),
        )
    }

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

    override suspend fun owedByDimension(dimensionIds: Collection<Long>): Map<Long, Double> =
        owedByDimensionByCurrency(dimensionIds).mapValues { it.value.soleValue() }

    override suspend fun owedByDimensionByCurrency(
        dimensionIds: Collection<Long>,
    ): Map<Long, MoneyByCurrency> {
        if (dimensionIds.isEmpty()) return emptyMap()
        // Liability entries are stored negative (credit); owed reads positive.
        return entryDao.naturalBalanceByDimension(dimensionIds.distinct())
            .groupBy { it.dimensionId!! }
            .mapValues { (_, rows) -> rows.toMoney(negated = true) { it.total } }
    }

    override suspend fun flowsByDimension(
        dimensionIds: Collection<Long>,
    ): Map<Long, DimensionFlows> =
        flowsByDimensionByCurrency(dimensionIds).mapValues { (_, flows) ->
            DimensionFlows(
                expense = flows.expense.soleValue(),
                advancePayment = flows.advancePayment.soleValue(),
                adjustment = flows.adjustment.soleValue(),
            )
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

    override suspend fun liabilityMonthFlows(month: YearMonth): LiabilityMonthFlows {
        val flows = liabilityMonthFlowsByCurrency(month)
        return LiabilityMonthFlows(
            expense = flows.expense.soleValue(),
            payment = flows.payment.soleValue(),
            adjustment = flows.adjustment.soleValue(),
        )
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

    override suspend fun assetMonthFlows(month: YearMonth): AssetMonthFlows {
        val flows = assetMonthFlowsByCurrency(month)
        return AssetMonthFlows(
            income = flows.income.soleValue(),
            expense = flows.expense.soleValue(),
            adjustment = flows.adjustment.soleValue(),
        )
    }

    override suspend fun assetMonthFlowsByCurrency(month: YearMonth): AssetMonthFlowsByCurrency {
        val rows = entryDao.assetMonthTotals(month.toString())
        return AssetMonthFlowsByCurrency(
            income = rows.toMoney { it.income },
            expense = rows.toMoney { it.expense },
            adjustment = rows.toMoney { it.adjustment },
        )
    }


    override suspend fun totalsByDimension(
        nominalType: AccountType,
        startDate: LocalDate,
        endDate: LocalDate,
        siblingAccountIds: List<Long>,
    ): Map<Long?, Double> =
        totalsByDimensionByCurrency(nominalType, startDate, endDate, siblingAccountIds)
            .mapValues { it.value.soleValue() }

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

    override suspend fun totalsByDimensionInScope(
        nominalType: AccountType,
        scopeDimensionIds: List<Long>,
    ): Map<Long?, Double> =
        totalsByDimensionInScopeByCurrency(nominalType, scopeDimensionIds)
            .mapValues { it.value.soleValue() }

    override suspend fun totalsByDimensionInScopeByCurrency(
        nominalType: AccountType,
        scopeDimensionIds: List<Long>,
    ): Map<Long?, MoneyByCurrency> {
        if (scopeDimensionIds.isEmpty()) return emptyMap()
        return entryDao
            .totalsByDimensionInScope(nominalType.name, scopeDimensionIds)
            .byDimension()
    }

    override suspend fun scopeStats(
        scopeAccountIds: List<Long>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): ScopeStats {
        val stats = scopeStatsByCurrency(scopeAccountIds, startDate, endDate)
        return ScopeStats(
            income = stats.income.soleValue(),
            expense = stats.expense.soleValue(),
            balance = stats.balance.soleValue(),
            openingBalance = stats.openingBalance.soleValue(),
        )
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

/**
 * The one number a deprecated scalar read still has to return while its callers
 * migrate to the per-currency one (task 13.1 deletes them all).
 *
 * It is **not** a sum across currencies — that is what design D8 forbids. It is the
 * only currency's value, and it is unreachable with more than one: while these
 * signatures exist, no production path can create a second currency (the account and
 * card forms gain the currency selector only in task 12.3/12.4, and the inertia test
 * of 2.6 fails the build if anything else tries).
 */
private fun MoneyByCurrency.soleValue(): Double =
    singleOrNull()?.value ?: if (isEmpty) {
        0.0
    } else {
        error(
            "A scalar ledger read found $currencies. Its caller has not migrated to " +
                "the per-currency read yet, and the ledger will not sum two currencies.",
        )
    }
