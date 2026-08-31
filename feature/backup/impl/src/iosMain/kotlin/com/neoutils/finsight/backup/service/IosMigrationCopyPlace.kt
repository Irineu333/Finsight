@file:OptIn(ExperimentalForeignApi::class)

package com.neoutils.finsight.backup.service

import com.neoutils.finsight.database.defaultDatabasePath
import com.neoutils.finsight.domain.vault.MigrationCopyPlace
import com.neoutils.finsight.ui.screen.backup.service.PRE_MIGRATION_BACKUP_NAME
import com.neoutils.finsight.ui.screen.backup.service.STAGED_PRE_MIGRATION_NAME
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager

/**
 * The app's sandbox, which is where the copy taken before a migration goes: it is the one
 * place iOS lets an app write with nothing pointed at and nothing resolved, and the app is
 * coming up — a folder the user chose is reached through a bookmark that may not resolve at
 * all (design D12).
 *
 * Both paths come from what `:core:database` and [IosBackupDestination] already decide, so
 * the copy lands in the folder the history lists and retention sweeps — Application Support,
 * excluded from iCloud's backup like everything else this app keeps (design D14).
 */
class IosMigrationCopyPlace : MigrationCopyPlace {

    override val archivePath: String get() = defaultDatabasePath()

    override fun stagedCopyPath(): String? {
        val directory = backupDirectory() ?: return null
        val staged = "$directory/$STAGED_PRE_MIGRATION_NAME"

        DATABASE_FILES.forEach { suffix ->
            NSFileManager.defaultManager.removeItemAtPath(staged + suffix, error = null)
        }
        return staged.takeIf { !NSFileManager.defaultManager.fileExistsAtPath(it) }
    }

    /**
     * `moveItemAtPath` refuses a destination that already holds a file, so the copy in force
     * is removed first — the one step of this that is not atomic, and the narrowest window
     * on offer without leaving Foundation's file coordination: two calls over a file that
     * has just been written, against a whole `VACUUM` of the archive onto a disk that may be
     * full. The journal files of the copy that was replaced go with it, because they
     * describe a file that no longer exists.
     */
    override fun settleStagedCopy(keep: Boolean) {
        val directory = backupDirectory() ?: return
        val staged = "$directory/$STAGED_PRE_MIGRATION_NAME"

        if (keep && NSFileManager.defaultManager.fileExistsAtPath(staged)) {
            val copy = "$directory/$PRE_MIGRATION_BACKUP_NAME"
            DATABASE_FILES.forEach { suffix ->
                NSFileManager.defaultManager.removeItemAtPath(copy + suffix, error = null)
            }
            NSFileManager.defaultManager.moveItemAtPath(staged, copy, error = null)
        }

        DATABASE_FILES.forEach { suffix ->
            NSFileManager.defaultManager.removeItemAtPath(staged + suffix, error = null)
        }
    }
}
