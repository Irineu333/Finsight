package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.signedImpact
import kotlinx.datetime.YearMonth

class CalculateInvoiceOverviewsUseCase {

    operator fun invoke(
        invoices: List<Invoice>,
        transactions: List<Transaction>,
        forYearMonth: YearMonth,
    ): InvoiceOverviewStats {
        val invoiceOverviews = invoices
            .filter { it.closingMonth == forYearMonth }
            .map { invoice ->
                val invoiceTransactions = transactions.filter {
                    it.invoice?.id == invoice.id && it.target == Transaction.Target.CREDIT_CARD
                }
                val expense = invoiceTransactions
                    .filter { it.type.isExpense }
                    .sumOf { it.amount }
                val advancePayment = invoiceTransactions
                    .filter { it.type == Transaction.Type.INCOME && it.target == Transaction.Target.CREDIT_CARD && it.isInvoicePayment }
                    .sumOf { it.amount }
                val adjustment = invoiceTransactions
                    .filter { it.type.isAdjustment }
                    .sumOf { it.amount }

                InvoiceOverviewResult(
                    invoiceId = invoice.id,
                    creditCardName = invoice.creditCard.name,
                    invoiceStatus = invoice.status,
                    expense = expense,
                    advancePayment = advancePayment,
                    adjustment = adjustment,
                    total = invoiceTransactions.sumOf { -it.signedImpact() }
                )
            }

        val creditCardOverview = CreditCardOverviewResult(
            expense = invoiceOverviews.sumOf { it.expense },
            advancePayment = invoiceOverviews.sumOf { it.advancePayment },
            adjustment = invoiceOverviews.sumOf { it.adjustment },
            total = invoiceOverviews.sumOf { it.total }
        )

        return InvoiceOverviewStats(
            invoiceOverviews = invoiceOverviews,
            creditCardOverview = creditCardOverview
        )
    }

    data class InvoiceOverviewStats(
        val invoiceOverviews: List<InvoiceOverviewResult>,
        val creditCardOverview: CreditCardOverviewResult
    )

    data class InvoiceOverviewResult(
        val invoiceId: Long,
        val creditCardName: String,
        val invoiceStatus: Invoice.Status,
        val expense: Double,
        val advancePayment: Double,
        val adjustment: Double,
        val total: Double
    )

    data class CreditCardOverviewResult(
        val expense: Double,
        val advancePayment: Double,
        val adjustment: Double,
        val total: Double
    )
}
