package com.neoutils.finsight.di

import com.neoutils.finsight.backup.JvmCaptureOrigin
import com.neoutils.finsight.backup.service.JvmBackupDestination
import com.neoutils.finsight.backup.service.JvmBackupFileService
import com.neoutils.finsight.backup.service.JvmMigrationCopyPlace
import com.neoutils.finsight.domain.model.CaptureOrigin
import com.neoutils.finsight.domain.vault.MigrationCopyPlace
import com.neoutils.finsight.ui.screen.backup.service.BackupDestination
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import org.koin.dsl.module

actual val backupPlatformModule = module {
    factory<BackupFileService> { JvmBackupFileService() }
    factory<BackupDestination> { JvmBackupDestination(ownCopy = get()) }
    factory<CaptureOrigin> { JvmCaptureOrigin() }
    factory<MigrationCopyPlace> { JvmMigrationCopyPlace() }
}
