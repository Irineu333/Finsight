package com.neoutils.finsight.backup.service

import android.content.Context
import com.neoutils.finsight.database.defaultDatabasePath
import com.neoutils.finsight.domain.vault.MigrationCopyPlace
import com.neoutils.finsight.ui.screen.backup.service.PRE_MIGRATION_BACKUP_NAME
import java.io.File

/**
 * The app's own storage, which is where the copy taken before a migration goes on every
 * platform and on Android for one reason more: it is the only place reachable with nothing
 * granted and nothing mounted, and the app is coming up.
 *
 * Both paths come from what `:core:database` and [AndroidBackupDestination] already decide,
 * rather than being spelled out again, so the copy cannot drift into a directory of its own.
 *
 * **It is the first rung's directory, whichever rung the vault is on.** `VACUUM INTO` writes
 * to a path and the app is still coming up, so there is no folder to write to and nothing
 * yet that could ask a provider for one. While the vault is pointed at a folder the person
 * chose, this copy is therefore somewhere the history does not list and retention does not
 * sweep — it is kept, and it is not reachable from the screen.
 */
class AndroidMigrationCopyPlace(
    private val appContext: Context,
) : MigrationCopyPlace {

    override val archivePath: String get() = defaultDatabasePath(appContext)

    override fun clearedCopyPath(): String? = try {
        val copy = File(backupDirectory(appContext), PRE_MIGRATION_BACKUP_NAME)
        DATABASE_FILES.forEach { suffix -> File(copy.absolutePath + suffix).delete() }
        copy.absolutePath.takeIf { !copy.exists() }
    } catch (cause: SecurityException) {
        null
    }
}
