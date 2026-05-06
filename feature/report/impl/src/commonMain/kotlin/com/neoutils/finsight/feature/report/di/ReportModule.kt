package com.neoutils.finsight.feature.report.di

import com.neoutils.finsight.feature.report.usecase.CalculateReportCategorySpendingUseCase
import com.neoutils.finsight.feature.report.usecase.CalculateReportStatsUseCase
import com.neoutils.finsight.feature.report.usecase.BuildReportViewerParamsUseCase
import com.neoutils.finsight.feature.report.screen.config.ReportConfigViewModel
import com.neoutils.finsight.feature.report.render.HtmlReportDocumentRenderer
import com.neoutils.finsight.feature.report.render.ReportDocumentRenderer
import com.neoutils.finsight.feature.report.screen.viewer.ReportViewerViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val reportModule = module {
    includes(reportPlatformModule)

    factory<ReportDocumentRenderer> { HtmlReportDocumentRenderer() }

    factory { CalculateReportStatsUseCase() }

    factory { CalculateReportCategorySpendingUseCase() }

    factory { BuildReportViewerParamsUseCase(get(), get()) }

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
            operationRepository = get(),
            accountRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            categoryRepository = get(),
            calculateReportStatsUseCase = get(),
            calculateReportCategorySpendingUseCase = get(),
            renderer = get(),
            operationUiMapper = get(),
            analytics = get(),
        )
    }
}

expect val reportPlatformModule: Module
