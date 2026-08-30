package com.neoutils.finsight.di

import com.neoutils.finsight.backup.IosCaptureOrigin
import com.neoutils.finsight.backup.service.IosBackupDestination
import com.neoutils.finsight.backup.service.IosBackupFileService
import com.neoutils.finsight.backup.service.IosMigrationCopyPlace
import com.neoutils.finsight.domain.model.CaptureOrigin
import com.neoutils.finsight.domain.vault.MigrationCopyPlace
import com.neoutils.finsight.ui.screen.backup.service.BackupDestination
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import org.koin.dsl.module

actual val backupPlatformModule = module {
    factory<BackupFileService> { IosBackupFileService() }
    factory<BackupDestination> { IosBackupDestination(ownCopy = get()) }
    factory<CaptureOrigin> { IosCaptureOrigin() }
    factory<MigrationCopyPlace> { IosMigrationCopyPlace() }
}
