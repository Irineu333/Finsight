package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.EntryDao
import com.neoutils.finsight.database.dao.EntryWithAccount
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.CardMonthFlows
import com.neoutils.finsight.domain.repository.InvoiceFlows
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
        ),
        amount = entry.amount,
        currency = entry.currency,
        invoiceId = entry.invoiceId,
    )

    private fun AccountEntity.Type.toDomain() = when (this) {
        AccountEntity.Type.ASSET -> AccountType.ASSET
        AccountEntity.Type.LIABILITY -> AccountType.LIABILITY
        AccountEntity.Type.INCOME -> AccountType.INCOME
        AccountEntity.Type.EXPENSE -> AccountType.EXPENSE
        AccountEntity.Type.EQUITY -> AccountType.EQUITY
    }

    override fun observeLedgerChanges(): Flow<Unit> = entryDao.observeEntryCount().map { }

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

    override suspend fun balanceInMonth(month: YearMonth, accountId: Long): Double {
        return entryDao.balanceInMonth(accountId, month.toString()) / CENTS_PER_UNIT
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

    override suspend fun entryCountInMonth(month: YearMonth, accountId: Long): Int {
        return entryDao.entryCountInMonth(accountId, month.toString())
    }

    override suspend fun invoiceOwed(invoiceId: Long): Double {
        // Liability entries are stored negative (credit); owed reads positive.
        return -entryDao.invoiceNaturalBalance(invoiceId) / CENTS_PER_UNIT
    }

    override suspend fun invoiceFlows(invoiceId: Long): InvoiceFlows {
        val totals = entryDao.invoicePeriodTotals(invoiceId)
        return InvoiceFlows(
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

    override suspend fun categoryTotals(
        categoryType: AccountType,
        startDate: LocalDate,
        endDate: LocalDate,
        siblingAccountIds: List<Long>,
    ): Map<Long, Double> {
        if (siblingAccountIds.isEmpty()) return emptyMap()
        return entryDao
            .categoryTotalsWithSiblingLeg(categoryType.name, startDate, endDate, siblingAccountIds)
            .associate { it.accountId to it.total / CENTS_PER_UNIT }
    }

    override suspend fun categoryTotalsForInvoices(
        categoryType: AccountType,
        invoiceIds: List<Long>,
    ): Map<Long, Double> {
        if (invoiceIds.isEmpty()) return emptyMap()
        return entryDao
            .categoryTotalsForInvoices(categoryType.name, invoiceIds)
            .associate { it.accountId to it.total / CENTS_PER_UNIT }
    }
}
