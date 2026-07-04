package com.neoutils.finsight.di

import com.neoutils.finsight.domain.usecase.AddSupportReplyUseCase
import com.neoutils.finsight.domain.usecase.CreateSupportIssueUseCase
import com.neoutils.finsight.ui.screen.support.SupportIssueViewModel
import com.neoutils.finsight.ui.screen.support.SupportViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

expect val supportPlatformModule: Module

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

    viewModel {
        SupportIssueViewModel(
            issueId = it.get(),
            supportRepository = get(),
            addSupportReplyUseCase = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }
}
