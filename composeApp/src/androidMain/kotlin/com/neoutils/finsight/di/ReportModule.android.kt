package com.neoutils.finsight.di

import com.neoutils.finsight.report.ActivityHolder
import com.neoutils.finsight.report.service.AndroidReportPrintService
import com.neoutils.finsight.report.service.AndroidReportShareService
import com.neoutils.finsight.report.ReportPrintService
import com.neoutils.finsight.report.ReportShareService
import org.koin.dsl.module

actual val reportPlatformModule = module {
    single { ActivityHolder() }
    single<ReportShareService> { AndroidReportShareService(context = get()) }
    single<ReportPrintService> { AndroidReportPrintService(activityHolder = get()) }
}
