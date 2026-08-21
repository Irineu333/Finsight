package com.neoutils.finsight.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The file the app serves its own database from, in the application's private storage —
 * which is why it takes a [Context] where the other platforms take nothing.
 */
fun defaultDatabasePath(context: Context): String =
    context.applicationContext.getDatabasePath("finsight.db").absolutePath

fun getDatabaseBuilder(
    context: Context,
    path: String = defaultDatabasePath(context),
): RoomDatabase.Builder<AppDatabase> {
    return Room.databaseBuilder<AppDatabase>(
        context = context.applicationContext,
        name = path,
    )
}
