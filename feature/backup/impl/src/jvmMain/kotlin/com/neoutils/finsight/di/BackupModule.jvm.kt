package com.neoutils.finsight.di

import com.neoutils.finsight.backup.JvmCaptureOrigin
import com.neoutils.finsight.backup.service.JvmBackupFileService
import com.neoutils.finsight.domain.model.CaptureOrigin
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import org.koin.dsl.module

actual val backupPlatformModule = module {
    factory<BackupFileService> { JvmBackupFileService() }
    factory<CaptureOrigin> { JvmCaptureOrigin() }
}
