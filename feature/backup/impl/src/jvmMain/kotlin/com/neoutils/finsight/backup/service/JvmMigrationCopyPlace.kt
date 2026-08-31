package com.neoutils.finsight.backup.service

import com.neoutils.finsight.database.defaultDatabasePath
import com.neoutils.finsight.domain.vault.MigrationCopyPlace
import com.neoutils.finsight.ui.screen.backup.service.PRE_MIGRATION_BACKUP_NAME
import com.neoutils.finsight.ui.screen.backup.service.STAGED_PRE_MIGRATION_NAME
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The desktop's `~/.finance/`, where the archive and the copies of it already live and
 * where nothing an uninstall does can reach — the one platform on which the app's own
 * storage and a folder the user could point at are the same place (design D3).
 *
 * Both paths are taken from what `:core:database` and [JvmBackupDestination] already decide
 * rather than spelled out again, so the copy lands in the folder the history lists and
 * retention sweeps, and cannot drift into a folder of its own.
 */
class JvmMigrationCopyPlace(
    override val archivePath: String = defaultDatabasePath(),
    private val directory: File = defaultBackupDirectory(),
) : MigrationCopyPlace {

    override fun stagedCopyPath(): String? = try {
        directory.mkdirs()
        val staged = staged()
        DATABASE_FILES.forEach { suffix -> File(staged.absolutePath + suffix).delete() }
        staged.absolutePath.takeIf { !staged.exists() }
    } catch (cause: SecurityException) {
        null
    }

    /**
     * The move replaces the copy in force in one step, so there is no instant at which
     * neither file is there — which is the whole reason the new one was written beside it.
     * The journal files of the copy that was replaced go afterwards, because they describe
     * a file that no longer exists.
     */
    override fun settleStagedCopy(keep: Boolean) {
        val staged = staged()
        try {
            if (keep && staged.isFile) {
                val copy = File(directory, PRE_MIGRATION_BACKUP_NAME)
                Files.move(
                    staged.toPath(),
                    copy.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
                JOURNAL_FILES.forEach { suffix -> File(copy.absolutePath + suffix).delete() }
            }
        } catch (cause: IOException) {
            // The copy in force is untouched by a move that did not happen, which is the
            // outcome this whole shape exists to guarantee.
        } catch (cause: SecurityException) {
            // The same, for a file system that refuses outright.
        } finally {
            DATABASE_FILES.forEach { suffix -> File(staged.absolutePath + suffix).delete() }
        }
    }

    private fun staged() = File(directory, STAGED_PRE_MIGRATION_NAME)
}
