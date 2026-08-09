package com.neoutils.finsight.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.dao.CurrencyScoped
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.entity.DimensionEntity
import com.neoutils.finsight.database.entity.EntryEntity
import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.domain.model.DimensionKind
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.LocalDate

/**
 * A ledger to run the module's own queries against.
 *
 * It opens [LedgerDatabase] — the verification database, holding the ledger's four
 * tables and nothing else — and seeds it through the production DAOs. That is the
 * whole point of these tests being here: they exercise the real `@Query` strings
 * over the real schema, so SQL and assertion cannot drift apart. The versions that
 * preceded them built tables by hand and *mirrored* the DAO's SQL into the test,
 * and drift is exactly what happened — one of them was still asserting over an
 * `entries.invoiceId` that v10 had removed, passing all along.
 */
internal fun ledgerDatabase(): LedgerDatabase =
    Room.inMemoryDatabaseBuilder<LedgerDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

/**
 * The currency every fixture defaults to, so the suites written before currency
 * mattered read exactly as they did. Naming it here rather than repeating the literal
 * is what makes a cross-currency case a one-word change at the call site.
 */
internal const val LEGACY_CURRENCY = "BRL"

/** One leg of a seeded transaction: where it posts, how much, and how it is classified. */
internal data class Leg(
    val accountId: Long,
    val cents: Long,
    val dimensionId: Long? = null,
    val currency: String = LEGACY_CURRENCY,
)

internal infix fun Long.posts(cents: Long) = Leg(accountId = this, cents = cents)

internal fun Leg.taggedWith(dimensionId: Long) = copy(dimensionId = dimensionId)

/** The same leg, denominated in [currency] — how a cross-currency case is written. */
internal infix fun Leg.inCurrency(currency: String) = copy(currency = currency)

/**
 * The one row a grouped aggregate produced, asserting there is exactly one. Most
 * suites seed a single currency, and reading the sole row keeps their assertions
 * saying what they said before the `GROUP BY` — while failing loudly if a second
 * currency ever appears where the suite assumes one.
 */
internal fun <T : CurrencyScoped> List<T>.sole(): T = single()

/** The row denominated in [currency], or `null` when the aggregate produced none. */
internal fun <T : CurrencyScoped> List<T>.forCurrency(currency: String): T? =
    firstOrNull { it.currency == currency }

internal class LedgerFixture(val database: LedgerDatabase) {

    private var nextTransactionId = 0L

    suspend fun account(
        id: Long,
        type: AccountEntity.Type,
        name: String = "account-$id",
        currency: String = LEGACY_CURRENCY,
        isArchived: Boolean = false,
    ): Long = database.accountDao().insert(
        AccountEntity(
            id = id,
            name = name,
            type = type,
            currency = currency,
            isArchived = isArchived,
        )
    )

    suspend fun dimension(id: Long, kind: DimensionKind): Long =
        database.dimensionDao().insert(DimensionEntity(id = id, kind = kind))

    /** One transaction on [date], with the legs given. Their sum is the caller's business. */
    suspend fun transaction(date: String, vararg legs: Leg): Long {
        val id = ++nextTransactionId
        database.transactionDao().insert(
            TransactionEntity(id = id, title = null, date = LocalDate.parse(date))
        )
        database.entryDao().insertAll(
            legs.map {
                EntryEntity(
                    transactionId = id,
                    accountId = it.accountId,
                    amount = it.cents,
                    currency = it.currency,
                    dimensionId = it.dimensionId,
                )
            }
        )
        return id
    }
}
