package com.neoutils.finsight.di

import com.neoutils.finsight.backup.JvmCaptureOrigin
import com.neoutils.finsight.backup.service.JvmBackupDestination
import com.neoutils.finsight.backup.service.JvmBackupFileService
import com.neoutils.finsight.backup.service.JvmBackupFolder
import com.neoutils.finsight.backup.service.JvmFolderBackupDestination
import com.neoutils.finsight.backup.service.JvmMigrationCopyPlace
import com.neoutils.finsight.domain.model.CaptureOrigin
import com.neoutils.finsight.domain.vault.MigrationCopyPlace
import com.neoutils.finsight.domain.vault.VaultDestinations
import com.neoutils.finsight.domain.vault.VaultFolder
import com.neoutils.finsight.ui.screen.backup.service.BackupDestination
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import com.neoutils.finsight.ui.screen.backup.service.BackupFolder
import org.koin.dsl.module

actual val backupPlatformModule = module {
    factory<BackupFileService> { JvmBackupFileService() }

    // The concrete type is bound as well as the contract, and only the desktop's own
    // destination resolves it: the folder is a path here, and the two classes that may know
    // that are in one module together (design D2).
    single { JvmBackupFolder(settings = get()) }
    factory<BackupFolder> { get<JvmBackupFolder>() }

    // Both rungs, and the preference that says which is in force. The desktop is the one
    // platform where the first rung already survives everything (design D3), so the second
    // adds reach rather than durability — a folder somebody syncs is what covers the
    // machine being lost.
    factory {
        VaultDestinations(
            state = get(),
            link = get<VaultFolder>().link,
            appStorage = JvmBackupDestination(ownCopy = get()),
            folder = JvmFolderBackupDestination(folder = get<JvmBackupFolder>(), ownCopy = get()),
        )
    }

    // The concrete type is bound as well as the contract: the migration is the one caller
    // that addresses both rungs at once, and the router is the only thing that holds them.
    factory<BackupDestination> { get<VaultDestinations>() }

    factory<CaptureOrigin> { JvmCaptureOrigin() }
    factory<MigrationCopyPlace> { JvmMigrationCopyPlace() }
}
