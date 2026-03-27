package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.signedImpact
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth

data class AccountUi(
    val account: Account,
    val initialBalance: Double,
    val balance: Double,
    val income: Double,
    val expense: Double,
    val adjustment: Double,
    val invoicePayment: Double,
    val advancePayment: Double,
) {
    constructor(
        account: Account,
        transactions: List<Transaction>,
        month: YearMonth,
    ) : this(
        account = account,
        initialBalance = transactions
            .filter { transaction -> transaction.date.yearMonth < month }
            .sumOf { transaction -> transaction.signedImpact() },
        balance = transactions
            .filter { transaction -> transaction.date.yearMonth <= month }
            .sumOf { transaction -> transaction.signedImpact() },
        income = transactions
            .filter { transaction ->
                transaction.date.yearMonth == month && transaction.type == Transaction.Type.INCOME
            }
            .sumOf { transaction -> transaction.amount },
        expense = transactions
            .filter { transaction ->
                transaction.date.yearMonth == month &&
                    transaction.type == Transaction.Type.EXPENSE &&
                    !transaction.isInvoicePayment
            }
            .sumOf { transaction -> transaction.amount },
        adjustment = transactions
            .filter { transaction ->
                transaction.date.yearMonth == month && transaction.type == Transaction.Type.ADJUSTMENT
            }
            .sumOf { transaction -> transaction.signedImpact() },
        invoicePayment = transactions
            .filter { transaction ->
                transaction.date.yearMonth == month &&
                    transaction.type == Transaction.Type.EXPENSE &&
                    transaction.isInvoicePayment
            }
            .sumOf { transaction -> transaction.amount },
        advancePayment = 0.0,
    )
}
