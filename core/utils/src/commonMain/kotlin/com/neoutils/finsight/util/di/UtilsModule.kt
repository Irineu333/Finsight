package com.neoutils.finsight.util.di

import com.neoutils.finsight.util.DebounceManager
import org.koin.dsl.module

val utilsModule = module {
    factory { DebounceManager(delayMillis = 500L) }
}
