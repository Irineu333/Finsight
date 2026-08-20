package com.neoutils.finsight.database

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * The file the app serves its own database from, in the user's document directory.
 * Nothing is created here: the path is only spelled out, and whoever opens it decides
 * whether it comes into being.
 */
@OptIn(ExperimentalForeignApi::class)
fun defaultDatabasePath(): String {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null
    )
    return requireNotNull(documentDirectory).path + "/finsight.db"
}

fun getDatabaseBuilder(path: String = defaultDatabasePath()): RoomDatabase.Builder<AppDatabase> {
    return Room.databaseBuilder<AppDatabase>(
        name = path,
    )
}
