package com.neoutils.finsight.di

import com.neoutils.finsight.report.ReportPrintService
import com.neoutils.finsight.report.ReportShareService
import com.neoutils.finsight.report.service.AndroidReportPrintService
import com.neoutils.finsight.report.service.AndroidReportShareService
import org.koin.dsl.module

actual val reportPlatformModule = module {
    factory<ReportShareService> { AndroidReportShareService() }
    factory<ReportPrintService> { AndroidReportPrintService() }
}
