package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.EntryDao
import com.neoutils.finsight.database.dao.EntryWithAccount
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.database.mapper.toDomain
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.CardMonthFlows
import com.neoutils.finsight.domain.repository.DimensionFlows
import com.neoutils.finsight.domain.repository.ReportStats
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

    override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double {
        val cents = if (accountId == null) {
            entryDao.assetsBalanceUpToMonth(target.toString())
        } else {
            entryDao.balanceUpToMonth(accountId, target.toString())
        }
        return cents / CENTS_PER_UNIT
    }

    override suspend fun balance(accountId: Long): Double {
        return entryDao.balanceOf(accountId) / CENTS_PER_UNIT
    }

    override suspend fun dimensionBalanceInMonth(month: YearMonth, dimensionId: Long): Double {
        return entryDao.dimensionBalanceInMonth(dimensionId, month.toString()) / CENTS_PER_UNIT
    }

    override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows {
        val totals = entryDao.accountPeriodTotals(accountId, month.toString())
        return AccountFlows(
            income = totals.income / CENTS_PER_UNIT,
            expense = totals.expense / CENTS_PER_UNIT,
            adjustment = totals.adjustment / CENTS_PER_UNIT,
            invoicePayment = totals.invoicePayment / CENTS_PER_UNIT,
        )
    }

    override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int {
        return entryDao.dimensionEntryCountInMonth(dimensionId, month.toString())
    }

    override suspend fun dimensionOwed(dimensionId: Long): Double {
        // Liability entries are stored negative (credit); owed reads positive.
        return -entryDao.dimensionNaturalBalance(dimensionId) / CENTS_PER_UNIT
    }

    override suspend fun dimensionFlows(dimensionId: Long): DimensionFlows {
        val totals = entryDao.dimensionPeriodTotals(dimensionId)
        return DimensionFlows(
            expense = totals.expense / CENTS_PER_UNIT,
            advancePayment = totals.advancePayment / CENTS_PER_UNIT,
            adjustment = totals.adjustment / CENTS_PER_UNIT,
        )
    }

    override suspend fun cardMonthFlows(month: YearMonth): CardMonthFlows {
        val totals = entryDao.cardMonthTotals(month.toString())
        return CardMonthFlows(
            expense = totals.expense / CENTS_PER_UNIT,
            payment = totals.payment / CENTS_PER_UNIT,
        )
    }

    override suspend fun netWorth(): Double {
        return entryDao.netWorthCents() / CENTS_PER_UNIT
    }

    override suspend fun totalsByDimension(
        nominalType: AccountType,
        startDate: LocalDate,
        endDate: LocalDate,
        siblingAccountIds: List<Long>,
    ): Map<Long?, Double> {
        if (siblingAccountIds.isEmpty()) return emptyMap()
        return entryDao
            .totalsByDimensionWithSiblingLeg(nominalType.name, startDate, endDate, siblingAccountIds)
            .associate { it.dimensionId to it.total / CENTS_PER_UNIT }
    }

    override suspend fun totalsByDimensionInScope(
        nominalType: AccountType,
        scopeDimensionIds: List<Long>,
    ): Map<Long?, Double> {
        if (scopeDimensionIds.isEmpty()) return emptyMap()
        return entryDao
            .totalsByDimensionInScope(nominalType.name, scopeDimensionIds)
            .associate { it.dimensionId to it.total / CENTS_PER_UNIT }
    }

    override suspend fun reportStats(
        scopeAccountIds: List<Long>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): ReportStats {
        if (scopeAccountIds.isEmpty()) return ReportStats(0.0, 0.0, 0.0, 0.0)
        val totals = entryDao.reportStats(scopeAccountIds, startDate, endDate)
        return ReportStats(
            income = totals.income / CENTS_PER_UNIT,
            expense = totals.expense / CENTS_PER_UNIT,
            balance = totals.balance / CENTS_PER_UNIT,
            openingBalance = totals.openingBalance / CENTS_PER_UNIT,
        )
    }
}
