package com.neoutils.finsight.di

import com.neoutils.finsight.extension.DeviceRegion
import com.neoutils.finsight.extension.RegionDeviceRegion
import org.koin.dsl.module

actual val commonPlatformModule = module {
    single<DeviceRegion> { RegionDeviceRegion() }
}
