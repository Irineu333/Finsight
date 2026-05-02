package com.neoutils.finsight.feature.report.di

import com.neoutils.finsight.feature.report.service.ReportPrintService
import com.neoutils.finsight.feature.report.service.ReportShareService
import com.neoutils.finsight.feature.report.service.AndroidReportPrintService
import com.neoutils.finsight.feature.report.service.AndroidReportShareService
import org.koin.dsl.module

actual val reportPlatformModule = module {
    factory<ReportShareService> { AndroidReportShareService() }
    factory<ReportPrintService> { AndroidReportPrintService() }
}
