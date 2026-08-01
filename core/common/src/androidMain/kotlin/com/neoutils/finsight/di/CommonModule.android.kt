package com.neoutils.finsight.di

import com.neoutils.finsight.extension.DeviceRegion
import com.neoutils.finsight.extension.TelephonyDeviceRegion
import org.koin.dsl.module

actual val commonPlatformModule = module {
    // The application context, put here by `androidContext(...)` at startup — the same
    // one `databasePlatformModule` takes.
    single<DeviceRegion> { TelephonyDeviceRegion(context = get()) }
}
