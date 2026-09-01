package com.neoutils.finsight.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.neoutils.finsight.database.snapshot.captureBeforeMigration

/**
 * The file the app serves its own database from, in the application's private storage —
 * which is why it takes a [Context] where the other platforms take nothing.
 */
fun defaultDatabasePath(context: Context): String =
    context.applicationContext.getDatabasePath("finsight.db").absolutePath

/**
 * The builder the app opens its own database from — and, when [captureInto] is given, the
 * moment the copy that precedes a migration is taken. Room's `build()` opens nothing and
 * the chain runs on the first access, so this is the last point at which the file still
 * holds what it held before the upgrade.
 *
 * @param captureInto where to write a copy of what the database already holds, taken
 * before Room migrates it: **a path, and it captures; none, and it does not**. Being given
 * one is the whole of the decision — this module consults no preference to weigh against it
 * — and removing the copy the previous migration left is the caller's too, like every file
 * this module writes and never deletes. A capture that cannot happen does not stop the app
 * from opening; see [captureBeforeMigration].
 */
fun getDatabaseBuilder(
    context: Context,
    path: String = defaultDatabasePath(context),
    captureInto: String? = null,
): RoomDatabase.Builder<AppDatabase> {
    captureInto?.let { captureBeforeMigration(databasePath = path, destinationPath = it) }
    return Room.databaseBuilder<AppDatabase>(
        context = context.applicationContext,
        name = path,
    )
}
