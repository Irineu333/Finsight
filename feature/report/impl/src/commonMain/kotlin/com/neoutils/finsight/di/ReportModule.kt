package com.neoutils.finsight.di

import com.neoutils.finsight.domain.usecase.CalculateReportCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.CalculateReportStatsUseCase
import com.neoutils.finsight.ui.screen.report.config.BuildReportViewerParamsUseCase
import com.neoutils.finsight.ui.screen.report.config.ReportConfigViewModel
import com.neoutils.finsight.ui.screen.report.render.HtmlReportDocumentRenderer
import com.neoutils.finsight.ui.screen.report.render.ReportDocumentRenderer
import com.neoutils.finsight.ui.screen.report.viewer.ReportViewerViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

expect val reportPlatformModule: Module

val reportModule = module {
    includes(reportPlatformModule)

    factory<ReportDocumentRenderer> { HtmlReportDocumentRenderer() }

    factory {
        CalculateReportStatsUseCase(
            entryRepository = get(),
            accountRepository = get(),
            creditCardRepository = get(),
        )
    }
    factory {
        CalculateReportCategorySpendingUseCase(
            entryRepository = get(),
            categoryRepository = get(),
            accountRepository = get(),
            creditCardRepository = get(),
        )
    }
    factory { BuildReportViewerParamsUseCase(get()) }

    viewModel {
        ReportConfigViewModel(
            accountRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            buildReportViewerParams = get(),
            analytics = get(),
        )
    }

    viewModel { params ->
        ReportViewerViewModel(
            params = params.get(),
            transactionRepository = get(),
            accountRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            calculateReportStatsUseCase = get(),
            calculateReportCategorySpendingUseCase = get(),
            entryRepository = get(),
            renderer = get(),
            analytics = get(),
        )
    }
}
