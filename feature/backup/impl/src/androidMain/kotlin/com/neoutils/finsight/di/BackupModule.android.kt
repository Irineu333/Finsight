package com.neoutils.finsight.di

import android.content.Context
import com.neoutils.finsight.backup.AndroidCaptureOrigin
import com.neoutils.finsight.backup.service.AndroidBackupDestination
import com.neoutils.finsight.backup.service.AndroidBackupFileService
import com.neoutils.finsight.backup.service.AndroidBackupFolder
import com.neoutils.finsight.backup.service.AndroidFolderBackupDestination
import com.neoutils.finsight.backup.service.AndroidMigrationCopyPlace
import com.neoutils.finsight.domain.model.CaptureOrigin
import com.neoutils.finsight.domain.vault.MigrationCopyPlace
import com.neoutils.finsight.domain.vault.VaultDestinations
import com.neoutils.finsight.domain.vault.VaultFolder
import com.neoutils.finsight.domain.vault.service.BackupDestination
import com.neoutils.finsight.domain.vault.service.BackupFileService
import com.neoutils.finsight.domain.vault.service.BackupFolder
import org.koin.dsl.module

actual val backupPlatformModule = module {
    // The `Context` is the application's, taken here because this module is the only one
    // that can see it: what it is for is the app's own temporary area and the package's
    // declared version, neither of which belongs to a screen.
    factory<BackupFileService> { AndroidBackupFileService(appContext = get<Context>()) }

    // The concrete type is bound as well as the contract, and only Android's own folder
    // destination resolves it: the tree `Uri` is a handle, and the two classes that may
    // know it are in one module together (design D2).
    single { AndroidBackupFolder(appContext = get<Context>(), settings = get()) }
    factory<BackupFolder> { get<AndroidBackupFolder>() }

    // Both rungs, and the preference that says which is in force. Neither survives the
    // package being removed on its own — the app's own storage goes with it, and so does
    // the persisted grant — but the *files* in a folder the person chose do, and pointing
    // at that folder again is what finds them (design D4).
    //
    // A third rung, of sorts: the folder [pointAt] most recently shifted aside, over its own
    // tree Uri (AndroidBackupFolder.previous) — never offered a picker, only ever asked to
    // list and read what a token it did not choose still names (task 11.10).
    factory {
        val previousFolder = AndroidBackupFolder.previous(appContext = get<Context>(), settings = get())
        VaultDestinations(
            state = get(),
            link = get<VaultFolder>().link,
            appStorage = AndroidBackupDestination(appContext = get<Context>(), ownCopy = get()),
            folder = AndroidFolderBackupDestination(
                appContext = get<Context>(),
                folder = get<AndroidBackupFolder>(),
                ownCopy = get(),
                files = get(),
            ),
            folderToken = get<AndroidBackupFolder>(),
            previousFolder = AndroidFolderBackupDestination(
                appContext = get<Context>(),
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

    factory<CaptureOrigin> { AndroidCaptureOrigin(context = get<Context>()) }
    factory<MigrationCopyPlace> { AndroidMigrationCopyPlace(appContext = get<Context>()) }
}
