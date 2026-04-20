package com.neoutils.finsight.database

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

internal fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbFile = File(System.getProperty("user.home"), ".finance/finsight.db")
    return Room.databaseBuilder<AppDatabase>(
        name = dbFile.absolutePath,
    )
}