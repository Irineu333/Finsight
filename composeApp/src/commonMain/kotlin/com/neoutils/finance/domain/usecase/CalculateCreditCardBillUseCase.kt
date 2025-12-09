@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Transaction
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth
import kotlin.time.ExperimentalTime

class CalculateCreditCardBillUseCase {
    operator fun invoke(
        target: YearMonth,
        transactions: List<Transaction>
    ): Double {
        return transactions
            .filter { it.date.yearMonth <= target }
            .filter { it.target.isCreditCard }
            .filter { it.type.isExpense }
            .sumOf { it.amount }
    }
}