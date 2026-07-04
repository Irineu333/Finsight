package com.neoutils.finsight.di

import com.neoutils.finsight.report.JvmReportPrintService
import com.neoutils.finsight.report.JvmReportShareService
import com.neoutils.finsight.ui.screen.report.service.ReportPrintService
import com.neoutils.finsight.ui.screen.report.service.ReportShareService
import org.koin.dsl.module

actual val reportPlatformModule = module {
    factory<ReportShareService> { JvmReportShareService() }
    factory<ReportPrintService> { JvmReportPrintService() }
}
