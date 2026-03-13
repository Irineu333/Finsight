package com.neoutils.finsight.di

import com.neoutils.finsight.report.service.PrintReportUseCase
import com.neoutils.finsight.domain.usecase.RenderHtmlReportUseCase
import com.neoutils.finsight.report.service.ShareReportUseCase
import org.koin.core.module.Module
import org.koin.dsl.module

val reportModule = module {
    includes(reportPlatformModule)

    factory { RenderHtmlReportUseCase() }
    factory { ShareReportUseCase(renderReport = get(), shareService = get()) }
    factory { PrintReportUseCase(renderReport = get(), printService = get()) }
}

expect val reportPlatformModule: Module
