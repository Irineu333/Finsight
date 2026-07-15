package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.EntryDao
import com.neoutils.finsight.domain.repository.IEntryRepository
import kotlinx.datetime.YearMonth

private const val CENTS_PER_UNIT = 100.0

class EntryRepository(
    private val entryDao: EntryDao,
) : IEntryRepository {

    override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double {
        val cents = if (accountId == null) {
            entryDao.assetsBalanceUpToMonth(target.toString())
        } else {
            entryDao.balanceUpToMonth(accountId, target.toString())
        }
        return cents / CENTS_PER_UNIT
    }

    override suspend fun balanceInMonth(month: YearMonth, accountId: Long): Double {
        return entryDao.balanceInMonth(accountId, month.toString()) / CENTS_PER_UNIT
    }

    override suspend fun invoiceOwed(invoiceId: Long): Double {
        // Liability entries are stored negative (credit); owed reads positive.
        return -entryDao.invoiceNaturalBalance(invoiceId) / CENTS_PER_UNIT
    }

    override suspend fun netWorth(): Double {
        return entryDao.netWorthCents() / CENTS_PER_UNIT
    }
}
