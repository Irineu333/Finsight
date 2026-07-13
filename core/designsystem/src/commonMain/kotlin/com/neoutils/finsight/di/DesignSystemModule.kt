package com.neoutils.finsight.di

import com.neoutils.finsight.ui.component.DetailPaneController
import com.neoutils.finsight.ui.component.ModalManager
import org.koin.dsl.module

val designsystemModule = module {
    single { DetailPaneController() }
    single { ModalManager(get()) }
}
