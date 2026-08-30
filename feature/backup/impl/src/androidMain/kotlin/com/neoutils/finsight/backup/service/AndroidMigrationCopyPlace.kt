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
 * so the copy lands in the folder the history lists and retention sweeps.
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
