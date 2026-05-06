package com.neoutils.finsight.feature.accounts.mapper

import com.neoutils.finsight.feature.accounts.model.Account
import com.neoutils.finsight.feature.accounts.model.AccountUi
import com.neoutils.finsight.feature.transactions.extension.signedImpact
import com.neoutils.finsight.feature.transactions.model.Transaction
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth

class AccountUiMapper : IAccountUiMapper {
    override fun toUi(
        account: Account,
        transactions: List<Transaction>,
        month: YearMonth,
    ): AccountUi {
        val initialBalance = transactions
            .filter { it.date.yearMonth < month }
            .sumOf { it.signedImpact() }

        val balance = transactions
            .filter { it.date.yearMonth <= month }
            .sumOf { it.signedImpact() }

        val income = transactions
            .filter { it.date.yearMonth == month && it.type == Transaction.Type.INCOME }
            .sumOf { it.amount }

        val expense = transactions
            .filter {
                it.date.yearMonth == month &&
                    it.type == Transaction.Type.EXPENSE &&
                    !it.isInvoicePayment
            }
            .sumOf { it.amount }

        val adjustment = transactions
            .filter { it.date.yearMonth == month && it.type == Transaction.Type.ADJUSTMENT }
            .sumOf { it.signedImpact() }

        val invoicePayment = transactions
            .filter {
                it.date.yearMonth == month &&
                    it.type == Transaction.Type.EXPENSE &&
                    it.isInvoicePayment
            }
            .sumOf { it.amount }

        return AccountUi(
            account = account,
            initialBalance = initialBalance,
            balance = balance,
            income = income,
            expense = expense,
            adjustment = adjustment,
            invoicePayment = invoicePayment,
            advancePayment = 0.0,
        )
    }
}