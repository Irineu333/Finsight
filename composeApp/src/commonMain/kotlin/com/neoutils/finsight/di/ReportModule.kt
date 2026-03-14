package com.neoutils.finsight.di

import com.neoutils.finsight.report.HtmlReportDocumentRenderer
import com.neoutils.finsight.report.ReportDocumentRenderer
import org.koin.core.module.Module
import org.koin.dsl.module

val reportModule = module {
    includes(reportPlatformModule)

    factory<ReportDocumentRenderer> { HtmlReportDocumentRenderer() }
}

expect val reportPlatformModule: Module
