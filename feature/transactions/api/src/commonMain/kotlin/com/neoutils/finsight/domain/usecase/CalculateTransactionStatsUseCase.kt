package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.deriveTransactionType
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth

/**
 * Month income/expense/adjustment across the ASSET legs of [operations] (task 4.11),
 * derived from each operation's ledger entries rather than legacy leg types. Callers
 * pass the operations they want counted (they already exclude transfers/payments by
 * kind); this reads the ASSET entry of each and classifies it with
 * [deriveTransactionType]. `expense` is the magnitude of the expense legs; `income`
 * the magnitude of the income legs; `adjustment` the signed sum of the adjustments.
 */
class CalculateTransactionStatsUseCase {
    operator fun invoke(
        operations: List<Operation>,
        forYearMonth: YearMonth,
    ): TransactionStats {
        var income = 0L
        var expense = 0L
        var adjustment = 0L
        operations
            .filter { it.date.yearMonth == forYearMonth }
            .forEach { operation ->
                operation.entries
                    .filter { it.account.type == AccountType.ASSET }
                    .forEach { entry ->
                        when (deriveTransactionType(entry.amount, operation.entries)) {
                            Transaction.Type.INCOME -> income += entry.amount
                            Transaction.Type.EXPENSE -> expense += -entry.amount
                            Transaction.Type.ADJUSTMENT -> adjustment += entry.amount
                        }
                    }
            }

        return TransactionStats(
            income = income / 100.0,
            expense = expense / 100.0,
            adjustment = adjustment / 100.0,
        )
    }

    data class TransactionStats(
        val income: Double,
        val expense: Double,
        val adjustment: Double,
    )
}
