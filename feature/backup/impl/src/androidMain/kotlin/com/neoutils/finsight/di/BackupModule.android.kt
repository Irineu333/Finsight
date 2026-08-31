package com.neoutils.finsight.di

import android.content.Context
import com.neoutils.finsight.backup.AndroidCaptureOrigin
import com.neoutils.finsight.backup.service.AndroidBackupDestination
import com.neoutils.finsight.backup.service.AndroidBackupFileService
import com.neoutils.finsight.backup.service.AndroidMigrationCopyPlace
import com.neoutils.finsight.domain.model.CaptureOrigin
import com.neoutils.finsight.domain.vault.MigrationCopyPlace
import com.neoutils.finsight.domain.vault.VaultDestinations
import com.neoutils.finsight.ui.screen.backup.service.BackupDestination
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import com.neoutils.finsight.ui.screen.backup.service.BackupFolder
import com.neoutils.finsight.ui.screen.backup.service.NoBackupFolder
import com.neoutils.finsight.ui.screen.backup.service.UnreachableDestination
import org.koin.dsl.module

actual val backupPlatformModule = module {
    // The `Context` is the application's, taken here because this module is the only one
    // that can see it: what it is for is the app's own temporary area and the package's
    // declared version, neither of which belongs to a screen.
    factory<BackupFileService> { AndroidBackupFileService(appContext = get<Context>()) }

    // The second rung is not built here yet (tasks 11.1–11.3). Both halves of it say so:
    // no picker is offered, so nothing can move the vault onto the folder, and the folder
    // destination refuses every operation rather than quietly writing into the app's own
    // storage. What 11.1 supplies is a `BackupFolder` over
    // `ActivityResultContracts.OpenDocumentTree` with the tree `Uri` persisted, and a
    // `BackupDestination` over `DocumentsContract` — under the shared subfolder name
    // `BACKUP_FOLDER_NAME`, which is what lets a reinstall find the archive again.
    factory<BackupFolder> { NoBackupFolder }

    factory<BackupDestination> {
        VaultDestinations(
            state = get(),
            appStorage = AndroidBackupDestination(appContext = get<Context>(), ownCopy = get()),
            folder = UnreachableDestination,
        )
    }

    factory<CaptureOrigin> { AndroidCaptureOrigin(context = get<Context>()) }
    factory<MigrationCopyPlace> { AndroidMigrationCopyPlace(appContext = get<Context>()) }
}
