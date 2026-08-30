package com.neoutils.finsight.database

import androidx.room.Room
import androidx.room.RoomDatabase
import com.neoutils.finsight.database.snapshot.captureBeforeMigration
import java.io.File

/**
 * The file the desktop app serves its own database from. Nothing is created here: the
 * path is only spelled out, and whoever opens it decides whether it comes into being.
 */
fun defaultDatabasePath(): String =
    File(System.getProperty("user.home"), ".finance/finsight.db").absolutePath

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
    path: String = defaultDatabasePath(),
    captureInto: String? = null,
): RoomDatabase.Builder<AppDatabase> {
    captureInto?.let { captureBeforeMigration(databasePath = path, destinationPath = it) }
    return Room.databaseBuilder<AppDatabase>(
        name = path,
    )
}
