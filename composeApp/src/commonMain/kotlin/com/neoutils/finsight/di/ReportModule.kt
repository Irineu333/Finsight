package com.neoutils.finsight.di

import com.neoutils.finsight.report.HtmlReportDocumentRenderer
import com.neoutils.finsight.report.ReportDocumentRenderer
import com.neoutils.finsight.report.service.PrintReportUseCase
import com.neoutils.finsight.report.service.ShareReportUseCase
import org.koin.core.module.Module
import org.koin.dsl.module

val reportModule = module {
    includes(reportPlatformModule)

    factory<ReportDocumentRenderer> { HtmlReportDocumentRenderer() }
    factory { ShareReportUseCase(renderer = get(), shareService = get()) }
    factory { PrintReportUseCase(renderer = get(), printService = get()) }
}

expect val reportPlatformModule: Module
