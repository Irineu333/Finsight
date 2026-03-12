package com.neoutils.finsight.di

import com.neoutils.finsight.report.JvmReportOutputService
import com.neoutils.finsight.report.ReportOutputService
import org.koin.dsl.module

actual val reportPlatformModule = module {
    single<ReportOutputService> { JvmReportOutputService() }
}
