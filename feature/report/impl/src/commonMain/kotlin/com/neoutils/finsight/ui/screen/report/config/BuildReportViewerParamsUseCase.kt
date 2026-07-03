package com.neoutils.finsight.ui.screen.report.config

import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.ui.screen.report.ReportViewerParams

class BuildReportViewerParamsUseCase(
    private val invoiceRepository: IInvoiceRepository,
) {
    suspend operator fun invoke(config: ReportConfig): ReportViewerParams? {
        if (!config.isValid) return null
        return when (config.selectedTab) {
            PerspectiveTab.ACCOUNT -> ReportViewerParams(
                perspectiveType = PerspectiveTab.ACCOUNT,
                accountIds = config.selectedAccountIds.toList(),
                startDate = config.startDate,
                endDate = config.endDate,
                includeSpendingByCategory = config.includeSpendingByCategory,
                includeIncomeByCategory = config.includeIncomeByCategory,
                includeTransactionList = config.includeTransactionList,
            )

            PerspectiveTab.CREDIT_CARD -> {
                val creditCardId = config.selectedCreditCardId ?: return null
                val invoices = invoiceRepository.getInvoicesByCreditCard(creditCardId)
                val selected = invoices.filter { it.id in config.selectedInvoiceIds }
                if (selected.isEmpty()) return null
                ReportViewerParams(
                    perspectiveType = PerspectiveTab.CREDIT_CARD,
                    creditCardId = creditCardId,
                    invoiceIds = selected.map { it.id },
                    startDate = selected.minOf { it.openingDate },
                    endDate = selected.maxOf { it.closingDate },
                    includeSpendingByCategory = config.includeSpendingByCategory,
                    includeIncomeByCategory = config.includeIncomeByCategory,
                    includeTransactionList = config.includeTransactionList,
                )
            }
        }
    }
}
