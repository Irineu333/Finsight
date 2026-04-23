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

val reportModule = module {
    includes(reportPlatformModule)

    factory<ReportDocumentRenderer> { HtmlReportDocumentRenderer() }

    factory { CalculateReportStatsUseCase() }

    factory { CalculateReportCategorySpendingUseCase() }

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
            operationRepository = get(),
            accountRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            calculateReportStatsUseCase = get(),
            calculateReportCategorySpendingUseCase = get(),
            renderer = get(),
            analytics = get(),
        )
    }
}

expect val reportPlatformModule: Module
