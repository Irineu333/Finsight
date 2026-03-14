package com.neoutils.finsight.di

import com.neoutils.finsight.report.JvmReportPrintService
import com.neoutils.finsight.report.JvmReportShareService
import com.neoutils.finsight.report.ReportPrintService
import com.neoutils.finsight.report.ReportShareService
import org.koin.dsl.module

actual val reportPlatformModule = module {
    factory<ReportShareService> { JvmReportShareService() }
    factory<ReportPrintService> { JvmReportPrintService() }
}
