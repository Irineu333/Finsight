package com.neoutils.finsight.di

import com.neoutils.finsight.backup.IosCaptureOrigin
import com.neoutils.finsight.backup.service.IosBackupDestination
import com.neoutils.finsight.backup.service.IosBackupFileService
import com.neoutils.finsight.backup.service.IosMigrationCopyPlace
import com.neoutils.finsight.domain.model.CaptureOrigin
import com.neoutils.finsight.domain.vault.MigrationCopyPlace
import com.neoutils.finsight.domain.vault.VaultDestinations
import com.neoutils.finsight.domain.vault.VaultFolder
import com.neoutils.finsight.ui.screen.backup.service.BackupDestination
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import com.neoutils.finsight.ui.screen.backup.service.BackupFolder
import com.neoutils.finsight.ui.screen.backup.service.NoBackupFolder
import com.neoutils.finsight.ui.screen.backup.service.UnreachableDestination
import org.koin.dsl.module

actual val backupPlatformModule = module {
    factory<BackupFileService> { IosBackupFileService() }

    // The second rung is not built here yet (tasks 11.4–11.5), and it is the one blocked on
    // an unanswered spike: Q1 asks whether a folder bookmark survives a reboot, and nobody
    // has run it on a device. What it will supply is a `BackupFolder` over
    // `UIDocumentPickerViewController` with `UTTypeFolder`, keeping the bookmark and never
    // letting the security-scoped `NSURL` through a `String` (design D2), and a
    // `BackupDestination` that balances `start`/`stopAccessingSecurityScopedResource` in a
    // `finally` — under the shared subfolder name `BACKUP_FOLDER_NAME`.
    factory<BackupFolder> { NoBackupFolder }

    factory {
        VaultDestinations(
            state = get(),
            link = get<VaultFolder>().link,
            appStorage = IosBackupDestination(ownCopy = get()),
            folder = UnreachableDestination,
        )
    }

    // The concrete type is bound as well as the contract: the migration is the one caller
    // that addresses both rungs at once, and the router is the only thing that holds them.
    factory<BackupDestination> { get<VaultDestinations>() }

    factory<CaptureOrigin> { IosCaptureOrigin() }
    factory<MigrationCopyPlace> { IosMigrationCopyPlace() }
}
