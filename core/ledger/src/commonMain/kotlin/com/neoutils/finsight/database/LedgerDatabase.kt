package com.neoutils.finsight.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import com.neoutils.finsight.database.dao.AccountDao
import com.neoutils.finsight.database.dao.DimensionDao
import com.neoutils.finsight.database.dao.EntryDao
import com.neoutils.finsight.database.dao.TransactionDao
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.entity.DimensionEntity
import com.neoutils.finsight.database.entity.EntryEntity
import com.neoutils.finsight.database.entity.TransactionEntity

/**
 * The ledger's tables and nothing else — which is the point.
 *
 * The app never opens this database; `AppDatabase` is the real one. It exists so
 * that KSP validates every `@Query` in this module against a schema in which
 * `invoices`, `categories` and `credit_cards` **do not exist** (design D9). Kotlin
 * visibility already stops a DAO importing a facade entity; only this stops the
 * table name appearing inside a SQL string, where the compiler would otherwise
 * never look — `AppDatabase` sees those tables, so the same query validates fine
 * over there.
 *
 * It is also the database the ledger's own query tests run on: they exercise the
 * production DAOs against exactly the schema the module claims to need.
 */
@Database(
    entities = [
        AccountEntity::class,
        TransactionEntity::class,
        EntryEntity::class,
        DimensionEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(LedgerConverters::class)
@ConstructedBy(LedgerDatabaseConstructor::class)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun entryDao(): EntryDao
    abstract fun dimensionDao(): DimensionDao
}

// Room compiler generates the actual implementations
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object LedgerDatabaseConstructor : RoomDatabaseConstructor<LedgerDatabase>
