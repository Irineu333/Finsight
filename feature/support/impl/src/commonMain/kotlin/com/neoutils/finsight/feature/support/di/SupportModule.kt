package com.neoutils.finsight.feature.support.di

import com.neoutils.finsight.feature.support.usecase.AddSupportReplyUseCase
import com.neoutils.finsight.feature.support.usecase.CreateSupportIssueUseCase
import com.neoutils.finsight.feature.support.screen.SupportIssueViewModel
import com.neoutils.finsight.feature.support.screen.SupportViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val supportModule = module {
    includes(supportPlatformModule)

    factory { CreateSupportIssueUseCase(supportRepository = get()) }
    factory { AddSupportReplyUseCase(supportRepository = get()) }

    viewModel {
        SupportViewModel(
            supportRepository = get(),
            createSupportIssueUseCase = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }

    viewModel { params ->
        SupportIssueViewModel(
            issueId = params.get(),
            supportRepository = get(),
            addSupportReplyUseCase = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }
}

expect val supportPlatformModule: Module
