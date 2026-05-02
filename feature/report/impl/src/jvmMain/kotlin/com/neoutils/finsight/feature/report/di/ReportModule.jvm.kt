package com.neoutils.finsight.feature.report.di

import com.neoutils.finsight.feature.report.JvmReportPrintService
import com.neoutils.finsight.feature.report.JvmReportShareService
import com.neoutils.finsight.feature.report.screen.service.ReportPrintService
import com.neoutils.finsight.feature.report.screen.service.ReportShareService
import org.koin.dsl.module

actual val reportPlatformModule = module {
    factory<ReportShareService> { JvmReportShareService() }
    factory<ReportPrintService> { JvmReportPrintService() }
}
