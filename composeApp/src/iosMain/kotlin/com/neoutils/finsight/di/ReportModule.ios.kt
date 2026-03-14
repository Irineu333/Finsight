package com.neoutils.finsight.di

import com.neoutils.finsight.report.IosReportPrintService
import com.neoutils.finsight.report.IosReportShareService
import com.neoutils.finsight.report.ReportPrintService
import com.neoutils.finsight.report.ReportShareService
import org.koin.dsl.module

actual val reportPlatformModule = module {
    factory<ReportShareService> { IosReportShareService() }
    factory<ReportPrintService> { IosReportPrintService() }
}
