package com.neoutils.finsight.di

import com.neoutils.finsight.report.HtmlReportDocumentRenderer
import com.neoutils.finsight.report.PlatformReportOutputService
import com.neoutils.finsight.report.ReportOutputService
import org.koin.dsl.module

val reportModule = module {
    single { HtmlReportDocumentRenderer() }
    single<ReportOutputService> { PlatformReportOutputService() }
}
