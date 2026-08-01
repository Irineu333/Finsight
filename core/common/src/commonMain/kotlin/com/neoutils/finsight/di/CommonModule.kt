package com.neoutils.finsight.di

import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.util.DebounceManager
import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.module

val commonModule = module {
    includes(commonPlatformModule)

    single<Settings> { Settings() }
    single { CurrencyFormatter() }
    factory { DebounceManager(delayMillis = 500L) }
}

/**
 * What only a platform can answer: the region the device is in
 * (`com.neoutils.finsight.extension.DeviceRegion`), which on Android needs the
 * application context and on the others does not.
 */
expect val commonPlatformModule: Module
