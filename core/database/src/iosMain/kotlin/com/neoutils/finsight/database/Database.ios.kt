package com.neoutils.finsight.database

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import com.neoutils.finsight.database.snapshot.captureBeforeMigration
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
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
    ).addCallback(ExcludeFromBackupCallback(path))
}

/**
 * Keeps the database out of iCloud's backup, where the document directory otherwise lands
 * by default — only `/tmp` and `/Library/Caches` are left out on their own.
 *
 * The exclusion covers the three files the database is made of in WAL mode, the `.db` and
 * its `-wal` and `-shm` companions, because they are copied as independent files and what
 * comes back may not add up to a database. Backup here is the file the user exports, and
 * that is the only copy the app promises.
 *
 * It runs on every open because the flag is a resource value of the file itself: a file
 * that does not exist yet cannot carry it — on a fresh install the companions appear only
 * once the database is opened — and a file operation may reset it.
 */
private class ExcludeFromBackupCallback(
    private val path: String,
) : RoomDatabase.Callback() {

    override fun onOpen(connection: SQLiteConnection) {
        listOf(path, "$path-wal", "$path-shm").forEach(::excludeFromBackup)
    }
}

/**
 * Marks one file as excluded from the backup. A file that is not there yet is simply
 * refused by the system, and the next open tries again.
 */
@OptIn(ExperimentalForeignApi::class)
private fun excludeFromBackup(path: String) {
    NSURL.fileURLWithPath(path).setResourceValue(
        value = true,
        forKey = NSURLIsExcludedFromBackupKey,
        error = null,
    )
}
