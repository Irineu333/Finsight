package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.signedCents
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth

data class AccountUi(
    val account: Account,
    val openingBalance: Double,
    val balance: Double,
    val income: Double,
    val expense: Double,
    val adjustment: Double,
    val invoicePayment: Double,
) {
    constructor(
        account: Account,
        transactions: List<Transaction>,
        month: YearMonth,
    ) : this(
        account = account,
        openingBalance = transactions
            .filter { transaction -> transaction.date.yearMonth < month }
            .sumOf { transaction -> transaction.signedCents() } / 100.0,
        balance = transactions
            .filter { transaction -> transaction.date.yearMonth <= month }
            .sumOf { transaction -> transaction.signedCents() } / 100.0,
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
            .sumOf { transaction -> transaction.signedCents() } / 100.0,
        invoicePayment = transactions
            .filter { transaction ->
                transaction.date.yearMonth == month &&
                    transaction.type == Transaction.Type.EXPENSE &&
                    transaction.isInvoicePayment
            }
            .sumOf { transaction -> transaction.amount },
    )
}
