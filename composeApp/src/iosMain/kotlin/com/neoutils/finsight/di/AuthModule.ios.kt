package com.neoutils.finsight.di

import com.neoutils.finsight.auth.FirebaseAuthService
import com.neoutils.finsight.domain.auth.AuthService
import org.koin.dsl.module

actual val authPlatformModule = module {
    single<AuthService> { FirebaseAuthService() }
}
