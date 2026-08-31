package com.neoutils.finsight.backup.service

import android.content.Context
import com.neoutils.finsight.database.defaultDatabasePath
import com.neoutils.finsight.domain.vault.MigrationCopyPlace
import com.neoutils.finsight.ui.screen.backup.service.PRE_MIGRATION_BACKUP_NAME
import com.neoutils.finsight.ui.screen.backup.service.STAGED_PRE_MIGRATION_NAME
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

    override fun stagedCopyPath(): String? = try {
        val staged = staged()
        DATABASE_FILES.forEach { suffix -> File(staged.absolutePath + suffix).delete() }
        staged.absolutePath.takeIf { !staged.exists() }
    } catch (cause: SecurityException) {
        null
    }

    /**
     * The rename replaces the copy in force in one step — a rename over an existing file is
     * an atomic replace on the file systems Android serves an app's own storage from — so
     * there is no instant at which neither file is there, which is the whole reason the new
     * one was written beside it. The journal files of the copy that was replaced go
     * afterwards, because they describe a file that no longer exists.
     */
    override fun settleStagedCopy(keep: Boolean) {
        val staged = staged()
        try {
            if (keep && staged.isFile) {
                val copy = File(backupDirectory(appContext), PRE_MIGRATION_BACKUP_NAME)
                if (staged.renameTo(copy)) {
                    JOURNAL_FILES.forEach { suffix -> File(copy.absolutePath + suffix).delete() }
                }
            }
        } catch (cause: SecurityException) {
            // The copy in force is untouched by a rename that did not happen, which is the
            // outcome this whole shape exists to guarantee.
        } finally {
            DATABASE_FILES.forEach { suffix -> File(staged.absolutePath + suffix).delete() }
        }
    }

    private fun staged() = File(backupDirectory(appContext), STAGED_PRE_MIGRATION_NAME)
}
