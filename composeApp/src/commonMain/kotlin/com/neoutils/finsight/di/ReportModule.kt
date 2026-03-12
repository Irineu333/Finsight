package com.neoutils.finsight.di

import com.neoutils.finsight.report.HtmlReportDocumentRenderer
import org.koin.core.module.Module
import org.koin.dsl.module

val reportModule = module {
    includes(reportPlatformModule)

    single { HtmlReportDocumentRenderer() }
}

expect val reportPlatformModule: Module
