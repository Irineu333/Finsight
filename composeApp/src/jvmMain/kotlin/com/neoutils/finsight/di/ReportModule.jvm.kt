package com.neoutils.finsight.di

import com.neoutils.finsight.ui.screen.reports.ReportExportService
import com.neoutils.finsight.ui.screen.reports.UnsupportedReportExportService
import org.koin.dsl.module

actual val reportPlatformModule = module {
    single<ReportExportService> {
        // TODO: implement desktop report export/share flow.
        UnsupportedReportExportService("Exportacao de relatorios ainda nao implementada no Desktop.")
    }
}
