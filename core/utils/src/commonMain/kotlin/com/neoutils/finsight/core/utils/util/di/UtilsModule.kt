package com.neoutils.finsight.core.utils.util.di

import com.neoutils.finsight.core.utils.util.DebounceManager
import org.koin.dsl.module

val utilsModule = module {
    factory { DebounceManager(delayMillis = 500L) }
}
