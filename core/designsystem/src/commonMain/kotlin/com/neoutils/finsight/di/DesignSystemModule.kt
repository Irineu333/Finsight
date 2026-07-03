package com.neoutils.finsight.di

import com.neoutils.finsight.ui.component.ModalManager
import org.koin.dsl.module

/**
 * Singletons de UI cross-cutting providos pelo core designsystem
 * (gerenciador de modais), consumidos por múltiplas features via Koin.
 */
val designsystemModule = module {
    single { ModalManager() }
}
