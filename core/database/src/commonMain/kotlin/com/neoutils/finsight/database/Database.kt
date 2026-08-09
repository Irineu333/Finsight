package com.neoutils.finsight.database

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.callback.CurrencySeedingCallback
import com.neoutils.finsight.database.migration.Migration10To11
import com.neoutils.finsight.database.migration.Migration11To12
import com.neoutils.finsight.database.migration.Migration12To13
import com.neoutils.finsight.database.migration.Migration13To14
import com.neoutils.finsight.database.migration.Migration1To2
import com.neoutils.finsight.database.migration.Migration2To3
import com.neoutils.finsight.database.migration.Migration3To4
import com.neoutils.finsight.database.migration.Migration4To5
import com.neoutils.finsight.database.migration.Migration5To6
import com.neoutils.finsight.database.migration.Migration6To7
import com.neoutils.finsight.database.migration.Migration7To10
import com.neoutils.finsight.domain.model.CurrencySeeding
import kotlinx.coroutines.Dispatchers

/**
 * The one place the database is assembled: every migration, in order, plus the
 * fresh-install callback that stands in for the migrations a new database never runs.
 *
 * A migration lives in one file of `migration/`, named after the hop it performs. The
 * ones that need something the module cannot reach — a currency code, a seeding — take
 * it as a constructor parameter; the rest are objects.
 *
 * @param relabelCurrency see [Migration11To12].
 * @param baseCurrency see [Migration12To13].
 * @param currencySeeding see [Migration13To14].
 */
fun getRoomDatabase(
    builder: RoomDatabase.Builder<AppDatabase>,
    relabelCurrency: String? = null,
    baseCurrency: String,
    currencySeeding: CurrencySeeding,
): AppDatabase {
    return builder
        .addMigrations(
            Migration1To2,
            Migration2To3,
            Migration3To4,
            Migration4To5,
            Migration5To6,
            Migration6To7,
            Migration7To10,
            Migration10To11,
            Migration11To12(relabelCurrency),
            Migration12To13(baseCurrency),
            Migration13To14(currencySeeding),
        )
        .addCallback(CurrencySeedingCallback(currencySeeding))
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
}
