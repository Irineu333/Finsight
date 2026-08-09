package com.neoutils.finsight.database.callback

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import com.neoutils.finsight.database.extension.seedCurrencies
import com.neoutils.finsight.domain.model.CurrencySeeding

/**
 * The fresh install's half of the seeding.
 *
 * A database created from the entities skips every migration, so the seeding needs this
 * second entry point — and it is the *same* write, called from here, rather than a second
 * one that could drift from it.
 */
internal class CurrencySeedingCallback(
    private val seeding: CurrencySeeding,
) : RoomDatabase.Callback() {

    override fun onCreate(connection: SQLiteConnection) {
        connection.seedCurrencies(seeding)
    }
}
