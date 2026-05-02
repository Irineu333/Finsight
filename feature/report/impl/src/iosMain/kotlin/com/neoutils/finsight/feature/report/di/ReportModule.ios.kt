package com.neoutils.finsight.feature.report.di

import com.neoutils.finsight.feature.report.IosReportPrintService
import com.neoutils.finsight.feature.report.IosReportShareService
import com.neoutils.finsight.feature.report.screen.service.ReportPrintService
import com.neoutils.finsight.feature.report.screen.service.ReportShareService
import org.koin.dsl.module

actual val reportPlatformModule = module {
    factory<ReportShareService> { IosReportShareService() }
    factory<ReportPrintService> { IosReportPrintService() }
}
