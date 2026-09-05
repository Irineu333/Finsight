@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The doubles the two transfer use cases share — registering one and correcting one
 * are the same rules over the same collaborators, so their fakes are the same too.
 */
internal class ClockOn(private val today: LocalDate) : Clock {
    override fun now(): Instant = today.atStartOfDayIn(TimeZone.currentSystemDefault())
}

internal class StaticAccounts(private val accounts: List<Account>) : IAccountRepository {
    override suspend fun getAccountById(accountId: Long): Account? =
        accounts.firstOrNull { it.id == accountId }

    override fun observeAllAccounts(): Flow<List<Account>> = flowOf(accounts)
    override suspend fun getAllAccounts(): List<Account> = accounts
    override suspend fun getAllAccountsIncludingClosed(): List<Account> = accounts
    override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = flowOf(accounts)
    override suspend fun getAllLedgerAccounts(): List<Account> = accounts
    override fun observeAllLedgerAccounts(): Flow<List<Account>> = flowOf(accounts)
    override fun observeAccountById(accountId: Long): Flow<Account?> =
        flowOf(accounts.firstOrNull { it.id == accountId })

    override suspend fun getDefaultAccount(): Account? = null
    override fun observeDefaultAccount(): Flow<Account?> = flowOf(null)
    override suspend fun getAccountCount(): Int = accounts.size
    override suspend fun hasYieldingAccount(): Boolean = false
    override suspend fun insert(account: Account): Long = throw NotImplementedError()
    override suspend fun update(account: Account) = throw NotImplementedError()
    override suspend fun delete(account: Account) = throw NotImplementedError()
    override suspend fun reopen(accountId: Long) = throw NotImplementedError()
}

/** What a rewrite stated, kept whole so a test can read the legs it wrote. */
internal data class RecordedRewrite(
    val id: Long,
    val title: String?,
    val date: LocalDate,
    val legs: List<TransactionLeg>,
    val contra: ContraLeg?,
)

internal class RewriteRecordingTransactions(
    private val stored: List<Transaction> = emptyList(),
) : ITransactionRepository {

    val rewrites = mutableListOf<RecordedRewrite>()
    val created = mutableListOf<TransactionIntent>()

    override suspend fun getTransactionsByIds(ids: Collection<Long>): List<Transaction> =
        stored.filter { it.id in ids }

    override suspend fun getTransactionsBetween(startDate: LocalDate, endDate: LocalDate): List<Transaction> = throw NotImplementedError()
    override suspend fun getExistingTransactionIds(ids: Collection<Long>): Set<Long> = throw NotImplementedError()

    override suspend fun getTransactionById(id: Long): Transaction? =
        stored.firstOrNull { it.id == id }

    override suspend fun updateTransaction(
        id: Long,
        title: String?,
        date: LocalDate,
        legs: List<TransactionLeg>,
        contra: ContraLeg?,
    ) {
        rewrites += RecordedRewrite(id, title, date, legs, contra)
    }

    override fun observeAllTransactions(): Flow<List<Transaction>> = flowOf(stored)
    override fun observeTransactionsBy(
        date: LocalDate?,
        dimensionId: Long?,
        accountId: Long?,
    ): Flow<List<Transaction>> = flowOf(stored)

    override fun observeTransactionById(id: Long): Flow<Transaction?> =
        flowOf(stored.firstOrNull { it.id == id })

    override suspend fun getAllTransactions(): List<Transaction> = stored
    override suspend fun createTransaction(intent: TransactionIntent): Transaction {
        created += intent
        return Transaction(id = stored.size + created.size.toLong(), title = intent.title, date = intent.date)
    }

    override suspend fun createTransactions(intents: List<TransactionIntent>): List<Transaction> =
        throw NotImplementedError()

    override suspend fun deleteTransactionById(id: Long) = throw NotImplementedError()
    override suspend fun deleteTransactionsByIds(ids: List<Long>) = throw NotImplementedError()
}

/**
 * The archive, recording both directions: what a correction writes and what it takes
 * away. A test that only watched the writes could not tell "harvests nothing" from
 * "harvests and then revokes".
 */
internal class RecordingRates(
    private val existing: List<ExchangeRate> = emptyList(),
) : IExchangeRateRepository {

    val saved = mutableListOf<ExchangeRate>()
    val removed = mutableListOf<ExchangeRate>()

    override suspend fun save(rate: ExchangeRate) { saved += rate }
    override suspend fun remove(rate: ExchangeRate) { removed += rate }

    override suspend fun rateAsOf(currency: String, date: LocalDate): ExchangeRate? =
        existing.firstOrNull { it.currency == currency && it.date == date }

    override suspend fun ratesAsOf(date: LocalDate): Map<String, ExchangeRate> =
        existing.filter { it.date == date }.associateBy { it.currency }

    override suspend fun rateBetween(from: String, to: String, date: LocalDate): ExchangeRate? =
        existing.firstOrNull { it.currency == from && it.counterCurrency == to && it.date == date }

    override fun observeAll(): Flow<List<ExchangeRate>> = flowOf(existing + saved)
    override suspend fun countNaming(currency: String): Int =
        existing.count { it.currency == currency || it.counterCurrency == currency }

}
