package com.neoutils.finsight.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
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

/** One leg of a seeded transaction: where it posts, how much, and how it is classified. */
internal data class Leg(
    val accountId: Long,
    val cents: Long,
    val dimensionId: Long? = null,
)

internal infix fun Long.posts(cents: Long) = Leg(accountId = this, cents = cents)

internal fun Leg.taggedWith(dimensionId: Long) = copy(dimensionId = dimensionId)

internal class LedgerFixture(val database: LedgerDatabase) {

    private var nextTransactionId = 0L

    suspend fun account(id: Long, type: AccountEntity.Type, name: String = "account-$id"): Long =
        database.accountDao().insert(AccountEntity(id = id, name = name, type = type))

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
                    currency = "BRL",
                    dimensionId = it.dimensionId,
                )
            }
        )
        return id
    }
}
