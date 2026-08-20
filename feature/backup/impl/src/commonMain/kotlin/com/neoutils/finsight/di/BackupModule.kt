package com.neoutils.finsight.di

import com.neoutils.finsight.ui.screen.backup.BackupViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The file dialogs and what the running app knows about itself: two things every platform
 * answers differently, and the only two this feature cannot state in common code.
 */
expect val backupPlatformModule: Module

val backupModule = module {
    includes(backupPlatformModule)

    viewModel {
        BackupViewModel(
            database = get(),
            candidateVerifier = get(),
            files = get(),
            captureOrigin = get(),
            modalManager = get(),
            clock = get(),
        )
    }
}
