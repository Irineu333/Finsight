package com.neoutils.finsight.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.entity.DimensionEntity
import com.neoutils.finsight.database.entity.EntryEntity
import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.database.repository.EntryRepository
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.BASE_CURRENCY
import com.neoutils.finsight.domain.model.CurrencyBalance
import com.neoutils.finsight.domain.model.DimensionKind
import com.neoutils.finsight.domain.repository.IEntryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The pair of gates every group of this change is run against, over a **real** database and
 * the real repository — every app figure is `Σ entries`, so a figure that is right here is
 * right wherever it is shown.
 *
 * They are two because either alone is blind:
 *
 * - the **single-currency** gate says a user whose accounts are all in the base currency
 *   sees exactly the numbers they saw before: one figure per read, in one currency, exact;
 * - the **foreign-currency** gate covers what the first cannot see. For a base-currency
 *   user the account's currency and the base coincide, so a figure wrongly denominated in
 *   the base is indistinguishable from a right one. With a single account in a currency
 *   that is *not* the base, the two texts differ, and every figure must carry the currency
 *   of its own account or facade — never the base.
 *
 * The second gate's other half — the same profile *with a rate on file*, which must still
 * convert nothing, since there was never more than one currency to reconcile — needs the
 * rate table (§4) and is asserted by the consolidation suite (task 5.5). What is provable
 * here is that no read consults a rate at all: the ledger has no dependency that could
 * supply one.
 */
class CurrencyDenominationGateTest {

    private val file: File = File.createTempFile("finsight-denomination", ".db").also { it.delete() }

    @AfterTest
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `every figure of a base-currency user is one exact figure in the base`() = runTest {
        val ledger = seed(currency = BASE_CURRENCY)

        ledger.assertEveryFigureIsDenominatedIn(BASE_CURRENCY)
    }

    @Test
    fun `every figure of a user with no base-currency account is denominated in the account's own`() = runTest {
        // The whole profile in dollars, the base still the base: this is the user the
        // distinction exists for — a Brazilian locale resolving BRL while not a cent of the
        // money is in reais.
        val ledger = seed(currency = "USD")

        ledger.assertEveryFigureIsDenominatedIn("USD")
    }

    @Test
    fun `the figures themselves are the ones they were before the reads became per currency`() = runTest {
        val ledger = seed(currency = BASE_CURRENCY)

        // Exactly the arithmetic of the seed, and the same numbers a single-currency user
        // read before any of this: nothing about grouping by currency moves a figure.
        // 1000 in, 100 spent, 20 paid off the card.
        assertEquals(880.0, ledger.balanceUpTo(MONTH, ACCOUNT_ID).amount)
        assertEquals(CurrencyBalance.of(BASE_CURRENCY, 880.0), ledger.naturalBalanceUpTo(MONTH, AccountType.ASSET))
        assertEquals(CurrencyBalance.of(BASE_CURRENCY, -60.0), ledger.naturalBalanceUpTo(MONTH, AccountType.LIABILITY))
        assertEquals(CurrencyBalance.of(BASE_CURRENCY, 60.0), ledger.dimensionOwed(INVOICE_DIMENSION))
        assertEquals(CurrencyBalance.of(BASE_CURRENCY, 100.0), ledger.dimensionBalanceInMonth(MONTH, CATEGORY_DIMENSION))

        val flows = ledger.accountFlows(MONTH, ACCOUNT_ID)
        assertEquals(1_000.0, flows.income)
        assertEquals(100.0, flows.expense)
        assertEquals(20.0, flows.settlement)
    }

    /**
     * Every read able to hold a currency, asked for its denomination. A figure of one
     * currency is **exact** by construction — there was nothing to reconcile — so the
     * assertion is that each read holds exactly one, and that it is [currency].
     */
    private suspend fun IEntryRepository.assertEveryFigureIsDenominatedIn(currency: String) {
        val figures = mapOf(
            "accumulated assets" to naturalBalanceUpTo(MONTH, AccountType.ASSET),
            "accumulated liabilities" to naturalBalanceUpTo(MONTH, AccountType.LIABILITY),
            "category spending" to dimensionBalanceInMonth(MONTH, CATEGORY_DIMENSION),
            "invoice owed" to dimensionOwed(INVOICE_DIMENSION),
            "invoice expense" to dimensionFlows(INVOICE_DIMENSION).expense,
            "card month expense" to liabilityMonthFlows(MONTH).expense,
            "card month payment" to liabilityMonthFlows(MONTH).payment,
            "asset month income" to assetMonthFlows(MONTH).income,
            "asset month expense" to assetMonthFlows(MONTH).expense,
            "report balance" to scopeStats(listOf(ACCOUNT_ID, CARD_ACCOUNT_ID), FIRST, LAST).balance,
            "report opening balance" to scopeStats(listOf(ACCOUNT_ID, CARD_ACCOUNT_ID), FIRST, LAST).openingBalance,
            "spending seen from the account" to
                totalsByDimension(AccountType.EXPENSE, FIRST, LAST, listOf(ACCOUNT_ID)).values.single(),
        )

        figures.forEach { (figure, balance) ->
            assertEquals(
                setOf(currency),
                balance.currencies,
                "$figure must be a single exact figure, denominated in $currency and in nothing else",
            )
        }

        // The reads scoped to one account carry the account's currency too, and it is the
        // account's — not the base, which for this profile is a currency the user has none of.
        assertEquals(currency, balanceUpTo(MONTH, ACCOUNT_ID).currency)
        assertEquals(currency, balance(ACCOUNT_ID).currency)
        assertEquals(currency, accountFlows(MONTH, ACCOUNT_ID).currency)
        assertEquals(currency, balance(CARD_ACCOUNT_ID).currency)
    }

    /**
     * One month of an ordinary life, entirely in [currency]: salary in, a categorised
     * expense out, a card purchase, and a payment of part of that card. Enough that every
     * read above has something to answer, and every classification rule is exercised.
     */
    private suspend fun seed(currency: String): IEntryRepository {
        val database = Room.databaseBuilder<AppDatabase>(name = file.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

        val accounts = database.accountDao()
        accounts.insert(account(ACCOUNT_ID, "Bank", AccountEntity.Type.ASSET, currency))
        accounts.insert(account(CARD_ACCOUNT_ID, "Card", AccountEntity.Type.LIABILITY, currency))
        accounts.insert(account(INCOME_ID, "Receitas", AccountEntity.Type.INCOME, currency))
        accounts.insert(account(EXPENSE_ID, "Despesas", AccountEntity.Type.EXPENSE, currency))

        database.dimensionDao().insert(DimensionEntity(id = CATEGORY_DIMENSION, kind = DimensionKind.CATEGORY))
        database.dimensionDao().insert(DimensionEntity(id = INVOICE_DIMENSION, kind = DimensionKind.INVOICE))

        // Salary 1000 in, groceries 100 out (categorised), a card purchase of 80 on the
        // invoice, and 20 of it paid — the card still owes 60.
        post(database, date = 5, currency, ACCOUNT_ID to 100_000L, INCOME_ID to -100_000L)
        post(
            database, date = 8, currency,
            ACCOUNT_ID to -10_000L,
            EXPENSE_ID to 10_000L,
            dimensionOf = mapOf(EXPENSE_ID to CATEGORY_DIMENSION),
        )
        post(
            database, date = 12, currency,
            CARD_ACCOUNT_ID to -8_000L,
            EXPENSE_ID to 8_000L,
            dimensionOf = mapOf(CARD_ACCOUNT_ID to INVOICE_DIMENSION),
        )
        post(
            database, date = 20, currency,
            ACCOUNT_ID to -2_000L,
            CARD_ACCOUNT_ID to 2_000L,
            dimensionOf = mapOf(CARD_ACCOUNT_ID to INVOICE_DIMENSION),
        )

        return EntryRepository(database.entryDao())
    }

    private fun account(id: Long, name: String, type: AccountEntity.Type, currency: String) =
        AccountEntity(id = id, name = name, type = type, currency = currency)

    private suspend fun post(
        database: AppDatabase,
        date: Int,
        currency: String,
        vararg legs: Pair<Long, Long>,
        dimensionOf: Map<Long, Long> = emptyMap(),
    ) {
        val transactionId = database.transactionDao().insert(
            TransactionEntity(title = null, date = LocalDate(MONTH.year, MONTH.month, date))
        )
        database.entryDao().insertAll(
            legs.map { (accountId, amount) ->
                EntryEntity(
                    transactionId = transactionId,
                    accountId = accountId,
                    amount = amount,
                    currency = currency,
                    dimensionId = dimensionOf[accountId],
                )
            }
        )
    }

    private companion object {
        val MONTH = YearMonth(2026, 4)
        val FIRST = LocalDate(2026, 4, 1)
        val LAST = LocalDate(2026, 4, 30)

        const val ACCOUNT_ID = 1L
        const val CARD_ACCOUNT_ID = 2L
        const val INCOME_ID = 100L
        const val EXPENSE_ID = 101L
        const val CATEGORY_DIMENSION = 10L
        const val INVOICE_DIMENSION = 20L
    }
}
