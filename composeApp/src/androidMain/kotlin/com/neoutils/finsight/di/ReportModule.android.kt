package com.neoutils.finsight.di

import com.neoutils.finsight.report.ActivityHolder
import com.neoutils.finsight.report.AndroidReportOutputService
import com.neoutils.finsight.report.ReportOutputService
import org.koin.dsl.module

actual val reportPlatformModule = module {
    single { ActivityHolder() }
    single<ReportOutputService> { AndroidReportOutputService(context = get(), activityHolder = get()) }
}
