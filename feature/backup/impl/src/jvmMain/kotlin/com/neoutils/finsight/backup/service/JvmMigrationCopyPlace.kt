package com.neoutils.finsight.backup.service

import com.neoutils.finsight.database.defaultDatabasePath
import com.neoutils.finsight.domain.vault.MigrationCopyPlace
import com.neoutils.finsight.ui.screen.backup.service.PRE_MIGRATION_BACKUP_NAME
import java.io.File

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

    override fun clearedCopyPath(): String? = try {
        directory.mkdirs()
        val copy = File(directory, PRE_MIGRATION_BACKUP_NAME)
        DATABASE_FILES.forEach { suffix -> File(copy.absolutePath + suffix).delete() }
        copy.absolutePath.takeIf { !copy.exists() }
    } catch (cause: SecurityException) {
        null
    }
}
