package com.neoutils.finsight.di

import com.neoutils.finsight.ui.screen.reports.ReportExportService
import com.neoutils.finsight.ui.screen.reports.AndroidReportExportService
import org.koin.dsl.module

actual val reportPlatformModule = module {
    single<ReportExportService> {
        AndroidReportExportService(context = get())
    }
}
