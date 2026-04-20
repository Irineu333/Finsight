package com.neoutils.finsight.di

import com.neoutils.finsight.auth.NoOpAuthService
import com.neoutils.finsight.domain.auth.AuthService
import org.koin.dsl.module

internal actual val authPlatformModule = module {
    single<AuthService> { NoOpAuthService() }
}
