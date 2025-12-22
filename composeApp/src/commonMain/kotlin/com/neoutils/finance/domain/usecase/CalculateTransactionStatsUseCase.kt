package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Transaction
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth

class CalculateTransactionStatsUseCase {
    operator fun invoke(
        transactions: List<Transaction>,
        forYearMonth: YearMonth,
    ): TransactionStats {
        val monthTransactions = transactions.filter { it.date.yearMonth == forYearMonth }
        val expense = monthTransactions.filter { it.type.isExpense }
        val adjustment = monthTransactions.filter { it.type.isAdjustment }

        return TransactionStats(
            income = monthTransactions.filter { it.type.isIncome }.sumOf { it.amount },
            accountExpense = expense.filter { it.target.isAccount }.sumOf { it.amount },
            creditCardExpense = expense.filter { it.target.isCreditCard }.sumOf { it.amount },
            accountAdjustment = adjustment.filter { it.target.isAccount }.sumOf { it.amount },
            creditCardAdjustment = adjustment.filter { it.target.isCreditCard }.sumOf { it.amount },
            invoicePayment = monthTransactions.filter { it.type.isInvoicePayment }.sumOf { it.amount },
            advancePayment = monthTransactions.filter { it.type.isAdvancePayment }.sumOf { it.amount },
            transactions = monthTransactions,
        )
    }

    data class TransactionStats(
        val income: Double,
        val accountExpense: Double,
        val creditCardExpense: Double,
        val accountAdjustment: Double,
        val creditCardAdjustment: Double,
        val invoicePayment: Double,
        val advancePayment: Double,
        val transactions: List<Transaction>
    ) {
        val expense = accountExpense + creditCardExpense
    }
}
