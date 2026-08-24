package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.AssetMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.DimensionFlowsByCurrency
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.repository.LiabilityMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.ScopeStatsByCurrency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.math.roundToLong

/**
 * The doubles a payment correction is exercised over — a ledger small enough to read
 * and faithful in the two respects the correction is judged by.
 *
 * It **balances per currency the way the write boundary does**: the legs stated are
 * written with the boundary's own sign rule, and the residue of each currency is posted
 * to that currency's conversion account, undimensioned. Without that a cross-currency
 * correction could not be asserted at all — the two ends are stated in different
 * currencies, and what completes them is precisely what is under test.
 *
 * And it **recomputes what an invoice owes from the entries**, rather than answering a
 * figure fixed beforehand: a correction is only observable as a change in that number.
 */
internal class InvoicePaymentLedger(vararg accounts: Account) {

    private val accountsById = accounts.associateBy { it.id }

    val entriesByTransaction = mutableMapOf<Long, List<Entry>>()
    val titleByTransaction = mutableMapOf<Long, String?>()
    val dateByTransaction = mutableMapOf<Long, LocalDate>()

    private var nextTransactionId = 0L

    fun create(title: String?, date: LocalDate, legs: List<TransactionLeg>): Long {
        val transactionId = ++nextTransactionId
        titleByTransaction[transactionId] = title
        write(transactionId, date, legs)
        return transactionId
    }

    fun write(transactionId: Long, date: LocalDate, legs: List<TransactionLeg>) {
        dateByTransaction[transactionId] = date

        val stated = legs.map { leg ->
            val account = accountsById.getValue(leg.accountId)
            Entry(
                transactionId = transactionId,
                account = account,
                amount = leg.cents(),
                dimensionId = leg.dimensionId,
            )
        }

        val residues = stated
            .groupBy { it.currency }
            .mapValues { (_, group) -> group.sumOf { it.amount } }

        // One currency balances on its own or not at all; two or more are completed,
        // each residue landing on its currency's conversion account and carrying no
        // dimension (design D15).
        val completion = if (residues.size < 2) emptyList() else residues.map { (currency, residue) ->
            Entry(
                transactionId = transactionId,
                account = conversionAccount(currency),
                amount = -residue,
            )
        }

        entriesByTransaction[transactionId] = stated + completion
    }

    /** What a sub-ledger owes, read positive, exactly as the ledger reads it. */
    fun owed(dimensionId: Long): Double = -entriesByTransaction.values
        .flatten()
        .filter { it.dimensionId == dimensionId }
        .sumOf { it.amount } / 100.0

    fun entriesOf(transactionId: Long): List<Entry> = entriesByTransaction[transactionId].orEmpty()

    /**
     * Money already owed on an invoice: the card's leg credited, its nominal
     * counterpart debited. It is the spending a payment is later made against.
     */
    fun seedSpending(date: LocalDate, cardAccountId: Long, dimensionId: Long, amount: Double) {
        val card = accountsById.getValue(cardAccountId)
        val cents = (amount * 100).roundToLong()
        val transactionId = ++nextTransactionId
        dateByTransaction[transactionId] = date
        titleByTransaction[transactionId] = null
        entriesByTransaction[transactionId] = listOf(
            Entry(transactionId = transactionId, account = card, amount = -cents, dimensionId = dimensionId),
            Entry(
                transactionId = transactionId,
                account = Account(
                    id = 800 + card.id,
                    name = "Expense",
                    type = AccountType.EXPENSE,
                    currency = card.currency,
                ),
                amount = cents,
            ),
        )
    }

    private fun conversionAccount(currency: String) = Account(
        id = 900 + currency.hashCode().toLong(),
        name = "Conversion",
        type = AccountType.CONVERSION,
        currency = currency,
    )

    private fun TransactionLeg.cents(): Long {
        val cents = (amount * 100).roundToLong()
        return when (type) {
            TransactionType.EXPENSE -> -cents
            TransactionType.INCOME -> cents
            TransactionType.ADJUSTMENT -> cents
        }
    }
}

/**
 * The two legs a payment states: what leaves the account, undimensioned, and what the
 * card's `LIABILITY` leg receives, carrying the invoice's dimension.
 *
 * @param leaving what leaves the paying account, when it is not what settles the
 * invoice — the whole of the cross-currency case.
 */
internal fun paymentLegs(
    cardAccountId: Long,
    payingAccountId: Long,
    dimensionId: Long,
    settling: Double,
    leaving: Double = settling,
) = listOf(
    TransactionLeg(
        type = TransactionType.EXPENSE,
        amount = leaving,
        accountId = payingAccountId,
    ),
    TransactionLeg(
        type = TransactionType.INCOME,
        amount = settling,
        accountId = cardAccountId,
        dimensionId = dimensionId,
    ),
)

internal class LedgerTransactionRepository(
    private val ledger: InvoicePaymentLedger,
) : ITransactionRepository {

    override suspend fun createTransaction(intent: TransactionIntent): Transaction {
        val id = ledger.create(intent.title, intent.date, intent.legs)
        return Transaction(
            id = id,
            title = intent.title,
            date = intent.date,
            entries = ledger.entriesOf(id),
        )
    }

    override suspend fun updateTransaction(
        id: Long,
        title: String?,
        date: LocalDate,
        legs: List<TransactionLeg>,
        contra: ContraLeg?,
    ) {
        ledger.titleByTransaction[id] = title
        ledger.write(id, date, legs)
    }

    override suspend fun getTransactionById(id: Long): Transaction? =
        ledger.dateByTransaction[id]?.let { date ->
            Transaction(
                id = id,
                title = ledger.titleByTransaction[id],
                date = date,
                entries = ledger.entriesOf(id),
            )
        }

    override fun observeAllTransactions(): Flow<List<Transaction>> = notUnderTest()
    override fun observeTransactionsBy(date: LocalDate?, dimensionId: Long?, accountId: Long?): Flow<List<Transaction>> = notUnderTest()
    override fun observeTransactionById(id: Long): Flow<Transaction?> = notUnderTest()
    override suspend fun getAllTransactions(): List<Transaction> = notUnderTest()
    override suspend fun createTransactions(intents: List<TransactionIntent>): List<Transaction> = notUnderTest()
    override suspend fun deleteTransactionById(id: Long) = notUnderTest()
    override suspend fun deleteTransactionsByIds(ids: List<Long>) = notUnderTest()
}

internal class LedgerEntryRepository(
    private val ledger: InvoicePaymentLedger,
) : IEntryRepository {

    override suspend fun dimensionOwedByCurrency(dimensionId: Long): MoneyByCurrency =
        MoneyByCurrency.of(
            currency = currencyOf(dimensionId),
            value = ledger.owed(dimensionId),
        )

    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> =
        ledger.entriesOf(transactionId)

    /** The currency a dimension's legs land in — one, by the card facade's guarantee. */
    private fun currencyOf(dimensionId: Long): String = ledger.entriesByTransaction.values
        .flatten()
        .firstOrNull { it.dimensionId == dimensionId }
        ?.currency
        ?: "BRL"

    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = notUnderTest()
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun balance(accountId: Long): Double = notUnderTest()
    override suspend fun hasEntries(accountId: Long): Boolean = notUnderTest()
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = notUnderTest()
    override suspend fun accountFlows(month: YearMonth, accountId: Long, yieldDimensionId: Long?): AccountFlows = notUnderTest()
    override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int = notUnderTest()
    override suspend fun accountBalanceUpTo(accountId: Long, target: LocalDate): Double = notUnderTest()
    override suspend fun balanceUpToByCurrency(target: YearMonth, excludedAccountIds: Set<Long>): MoneyByCurrency = notUnderTest()
    override suspend fun naturalBalanceUpToByCurrency(target: YearMonth, type: AccountType, excludedAccountIds: Set<Long>): MoneyByCurrency = notUnderTest()
    override suspend fun dimensionMonthlySeriesByCurrency(dimensionId: Long, upTo: YearMonth): Map<YearMonth, MoneyByCurrency> = notUnderTest()
    override suspend fun dimensionBalanceInMonthByCurrency(month: YearMonth, dimensionId: Long): MoneyByCurrency = notUnderTest()
    override suspend fun dimensionFlowsByCurrency(dimensionId: Long): DimensionFlowsByCurrency = notUnderTest()
    override suspend fun owedByDimensionByCurrency(dimensionIds: Collection<Long>): Map<Long, MoneyByCurrency> = notUnderTest()
    override suspend fun flowsByDimensionByCurrency(dimensionIds: Collection<Long>): Map<Long, DimensionFlowsByCurrency> = notUnderTest()
    override suspend fun liabilityMonthFlowsByCurrency(month: YearMonth): LiabilityMonthFlowsByCurrency = notUnderTest()
    override suspend fun assetMonthFlowsByCurrency(month: YearMonth, yieldDimensionId: Long?): AssetMonthFlowsByCurrency = notUnderTest()
    override suspend fun totalsByDimensionByCurrency(
        nominalType: AccountType,
        startDate: LocalDate,
        endDate: LocalDate,
        siblingAccountIds: List<Long>,
    ): Map<Long?, MoneyByCurrency> = notUnderTest()
    override suspend fun totalsByDimensionInMonthByCurrency(
        month: YearMonth,
        nominalType: AccountType,
    ): Map<Long?, MoneyByCurrency> = notUnderTest()
    override suspend fun totalsByDimensionInScopeByCurrency(
        nominalType: AccountType,
        scopeDimensionIds: List<Long>,
    ): Map<Long?, MoneyByCurrency> = notUnderTest()
    override suspend fun scopeStatsByCurrency(
        scopeAccountIds: List<Long>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): ScopeStatsByCurrency = notUnderTest()
}

/** The archive, remembering what a crossing taught it. */
internal class RecordingExchangeRates : IExchangeRateRepository {

    val saved = mutableListOf<ExchangeRate>()

    override suspend fun save(rate: ExchangeRate) { saved += rate }

    override suspend fun rateAsOf(currency: String, date: LocalDate): ExchangeRate? = null
    override suspend fun ratesAsOf(date: LocalDate): Map<String, ExchangeRate> = emptyMap()
    override suspend fun rateBetween(from: String, to: String, date: LocalDate): ExchangeRate? = null
    override fun observeAll(): Flow<List<ExchangeRate>> = flowOf(emptyList())
    override suspend fun remove(rate: ExchangeRate) = Unit
    override suspend fun countNaming(currency: String): Int = 0
    override suspend fun removeAllNaming(currency: String) = Unit
}

private fun notUnderTest(): Nothing = error("not part of the payment correction under test")
