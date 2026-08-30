package com.neoutils.finsight.di

import android.content.Context
import com.neoutils.finsight.backup.AndroidCaptureOrigin
import com.neoutils.finsight.backup.service.AndroidBackupDestination
import com.neoutils.finsight.backup.service.AndroidBackupFileService
import com.neoutils.finsight.backup.service.AndroidMigrationCopyPlace
import com.neoutils.finsight.domain.model.CaptureOrigin
import com.neoutils.finsight.domain.vault.MigrationCopyPlace
import com.neoutils.finsight.ui.screen.backup.service.BackupDestination
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import org.koin.dsl.module

actual val backupPlatformModule = module {
    // The `Context` is the application's, taken here because this module is the only one
    // that can see it: what it is for is the app's own temporary area and the package's
    // declared version, neither of which belongs to a screen.
    factory<BackupFileService> { AndroidBackupFileService(appContext = get<Context>()) }
    factory<BackupDestination> {
        AndroidBackupDestination(appContext = get<Context>(), ownCopy = get())
    }
    factory<CaptureOrigin> { AndroidCaptureOrigin(context = get<Context>()) }
    factory<MigrationCopyPlace> { AndroidMigrationCopyPlace(appContext = get<Context>()) }
}
