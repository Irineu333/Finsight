package com.neoutils.finsight.di

import com.neoutils.finsight.ui.component.ModalManager
import org.koin.dsl.module

val designsystemModule = module {
    single { ModalManager() }
}
