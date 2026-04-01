package com.neoutils.finsight.di

import com.neoutils.finsight.ui.screen.report.render.HtmlReportDocumentRenderer
import com.neoutils.finsight.ui.screen.report.render.ReportDocumentRenderer
import org.koin.core.module.Module
import org.koin.dsl.module

val reportModule = module {
    includes(reportPlatformModule)

    factory<ReportDocumentRenderer> { HtmlReportDocumentRenderer() }
}

expect val reportPlatformModule: Module
