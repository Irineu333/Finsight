package com.neoutils.finsight.di

import com.neoutils.finsight.backup.IosCaptureOrigin
import com.neoutils.finsight.backup.service.IosBackupDestination
import com.neoutils.finsight.backup.service.IosBackupFileService
import com.neoutils.finsight.backup.service.IosBackupFolder
import com.neoutils.finsight.backup.service.IosFolderBackupDestination
import com.neoutils.finsight.backup.service.IosMigrationCopyPlace
import com.neoutils.finsight.domain.model.CaptureOrigin
import com.neoutils.finsight.domain.vault.MigrationCopyPlace
import com.neoutils.finsight.domain.vault.VaultDestinations
import com.neoutils.finsight.domain.vault.VaultFolder
import com.neoutils.finsight.domain.vault.service.BackupDestination
import com.neoutils.finsight.domain.vault.service.BackupFileService
import com.neoutils.finsight.domain.vault.service.BackupFolder
import org.koin.dsl.module

actual val backupPlatformModule = module {
    factory<BackupFileService> { IosBackupFileService() }

    // The concrete type is bound as well as the contract, and only iOS's own folder
    // destination resolves it: the folder is a security-scoped `NSURL` behind a bookmark,
    // and the two classes that may reach it are in one module together (design D2).
    single { IosBackupFolder() }
    factory<BackupFolder> { get<IosBackupFolder>() }

    // Both rungs, and the preference that says which is in force. Neither survives the app
    // being deleted on its own — the sandbox goes whole, and the bookmark with it — but the
    // *files* in a folder the person chose do, and pointing at that folder again is what
    // finds them (design D4).
    //
    // A third rung, of sorts: the folder [pointAt] most recently shifted aside, over its own
    // bookmark (IosBackupFolder.previous) — never offered a picker, only ever asked to list
    // and read what a bookmark it did not choose still names (task 11.10).
    factory {
        val previousFolder = IosBackupFolder.previous()
        VaultDestinations(
            state = get(),
            link = get<VaultFolder>().link,
            appStorage = IosBackupDestination(ownCopy = get()),
            folder = IosFolderBackupDestination(
                folder = get<IosBackupFolder>(),
                ownCopy = get(),
                files = get(),
            ),
            folderToken = get<IosBackupFolder>(),
            previousFolder = IosFolderBackupDestination(
                folder = previousFolder,
                ownCopy = get(),
                files = get(),
            ),
            previousFolderToken = previousFolder,
        )
    }

    // The concrete type is bound as well as the contract: the migration is the one caller
    // that addresses both rungs at once, and the router is the only thing that holds them.
    factory<BackupDestination> { get<VaultDestinations>() }

    factory<CaptureOrigin> { IosCaptureOrigin() }
    factory<MigrationCopyPlace> { IosMigrationCopyPlace() }
}
