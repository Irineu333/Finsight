package com.neoutils.finsight.core.auth.di

import com.neoutils.finsight.core.auth.FirebaseAuthService
import com.neoutils.finsight.core.auth.AuthService
import org.koin.dsl.module

internal actual val authPlatformModule = module {
    single<AuthService> { FirebaseAuthService() }
}
