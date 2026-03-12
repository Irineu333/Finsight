package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.ReportPerspective
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.signedImpact
import kotlinx.datetime.LocalDate

class CalculateReportStatsUseCase {
    operator fun invoke(
        operations: List<Operation>,
        perspective: ReportPerspective,
        startDate: LocalDate,
        endDate: LocalDate,
    ): ReportStats {
        val filtered = operations
            .filter { it.date in startDate..endDate }
            .filter { it.matchesPerspective(perspective) }

        val transactions = filtered.flatMap { operation ->
            operation.reportTransactionsFor(perspective)
        }

        val income = transactions
            .filter { it.type.isIncome }
            .sumOf { it.amount }

        val expense = transactions
            .filter { it.type.isExpense }
            .sumOf { it.amount }

        val balance = transactions.sumOf { it.signedImpact() }

        val priorTransactions = operations
            .filter { it.date < startDate }
            .filter { it.matchesPerspective(perspective) }
            .flatMap { operation ->
                operation.reportTransactionsFor(perspective)
            }

        val initialBalance = priorTransactions.sumOf { it.signedImpact() }

        return ReportStats(
            income = income,
            expense = expense,
            balance = balance,
            initialBalance = initialBalance,
        )
    }

    data class ReportStats(
        val income: Double,
        val expense: Double,
        val balance: Double,
        val initialBalance: Double,
    )
}

private fun Operation.matchesPerspective(perspective: ReportPerspective): Boolean {
    return when (perspective) {
        is ReportPerspective.AccountPerspective -> {
            perspective.accountIds.isEmpty() ||
                transactions.any {
                    it.target == Transaction.Target.ACCOUNT &&
                        (it.account?.id in perspective.accountIds)
                }
        }
        is ReportPerspective.CreditCardPerspective -> {
            transactions.any {
                it.target == Transaction.Target.CREDIT_CARD &&
                    it.creditCard?.id == perspective.creditCardId
            }
        }
    }
}

private fun Transaction.matchesPerspective(perspective: ReportPerspective): Boolean {
    return when (perspective) {
        is ReportPerspective.AccountPerspective -> {
            target == Transaction.Target.ACCOUNT &&
                (perspective.accountIds.isEmpty() || account?.id in perspective.accountIds)
        }
        is ReportPerspective.CreditCardPerspective -> {
            target == Transaction.Target.CREDIT_CARD &&
                creditCard?.id == perspective.creditCardId
        }
    }
}

private fun Operation.reportTransactionsFor(perspective: ReportPerspective): List<Transaction> {
    val matchingTransactions = transactions.filter { it.matchesPerspective(perspective) }
    if (perspective !is ReportPerspective.AccountPerspective) return matchingTransactions
    if (!isInternalTransferFor(perspective)) return matchingTransactions
    return emptyList()
}

private fun Operation.isInternalTransferFor(
    perspective: ReportPerspective.AccountPerspective
): Boolean {
    if (kind != Operation.Kind.TRANSFER) return false

    val transferAccountIds = transactions
        .filter { it.target == Transaction.Target.ACCOUNT }
        .mapNotNull { it.account?.id }
        .toSet()

    if (transferAccountIds.size < 2) return false

    return if (perspective.accountIds.isEmpty()) {
        true
    } else {
        transferAccountIds.all { it in perspective.accountIds }
    }
}
