package com.neoutils.finsight.e2e.di

import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.auth.AuthService
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.repository.ISupportRepository
import com.neoutils.finsight.e2e.analytics.E2eAnalytics
import com.neoutils.finsight.e2e.auth.E2eAuthService
import com.neoutils.finsight.e2e.crashlytics.E2eCrashlytics
import com.neoutils.finsight.e2e.support.InMemorySupportRepository
import org.koin.core.module.Module
import org.koin.dsl.module

val e2eOverridesModule: Module = module {
    single<AuthService> { E2eAuthService() }
    single<Analytics> { E2eAnalytics() }
    single<Crashlytics> { E2eCrashlytics() }
    single<ISupportRepository> { InMemorySupportRepository() }
}
