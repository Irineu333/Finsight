package com.neoutils.finsight.di

import com.neoutils.finsight.feature.shell.api.NavCatalog
import com.neoutils.finsight.ui.navigation.AppNavCatalog
import org.koin.dsl.module

val shellModule = module {
    single<NavCatalog> { AppNavCatalog }
}
