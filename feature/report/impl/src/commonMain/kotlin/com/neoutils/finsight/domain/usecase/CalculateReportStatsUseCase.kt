package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.extension.deriveTransactionType
import kotlinx.datetime.LocalDate

/**
 * The ledger scope a report is computed over. Resolved by the caller from a
 * `ReportPerspective`, because the perspective speaks in facades (a credit-card id)
 * while the ledger speaks in accounts (that card's LIABILITY account).
 */
sealed interface ReportLedgerScope {
    /** An account perspective. Empty [accountIds] means every ASSET account. */
    data class Accounts(val accountIds: Set<Long>) : ReportLedgerScope

    /** A single card, addressed by its LIABILITY ledger account (null when the card has none yet). */
    data class Card(val liabilityAccountId: Long?) : ReportLedgerScope
}

/**
 * Report figures derived entirely from the ledger (tasks 4.6/4.7): each transaction's
 * hydrated [Entry] legs, classified by [deriveTransactionType] and by account type —
 * no `TransactionType`, `TransactionTarget` or `Transaction.Kind`. `income`/`expense`
 * are the magnitudes of the scope's income/expense legs in the period; `balance` is
 * their signed sum (adjustments included, signed); `openingBalance` is the signed sum
 * of the scope's legs before the period. Internal transfers — transactions whose ASSET
 * legs all fall inside an account scope — are excluded, exactly as before.
 */
class CalculateReportStatsUseCase {
    operator fun invoke(
        transactions: List<Transaction>,
        scope: ReportLedgerScope,
        startDate: LocalDate,
        endDate: LocalDate,
    ): ReportStats {
        fun Transaction.scopeEntries(): List<Entry> = entries.filter { it.inScope(scope) }

        fun Transaction.isInternalTransfer(): Boolean {
            if (scope !is ReportLedgerScope.Accounts) return false
            val assetIds = entries
                .filter { it.account.type == AccountType.ASSET }
                .map { it.account.id }
                .toSet()
            if (assetIds.size < 2) return false
            return scope.accountIds.isEmpty() || assetIds.all { it in scope.accountIds }
        }

        var income = 0L
        var expense = 0L
        var balance = 0L
        transactions
            .filter { it.date in startDate..endDate && !it.isInternalTransfer() }
            .forEach { transaction ->
                transaction.scopeEntries().forEach { entry ->
                    balance += entry.amount
                    when (deriveTransactionType(entry.amount, transaction.entries)) {
                        TransactionType.INCOME -> income += entry.amount
                        TransactionType.EXPENSE -> expense += -entry.amount
                        TransactionType.ADJUSTMENT -> Unit
                    }
                }
            }

        var openingBalance = 0L
        transactions
            .filter { it.date < startDate && !it.isInternalTransfer() }
            .forEach { transaction ->
                transaction.scopeEntries().forEach { entry -> openingBalance += entry.amount }
            }

        return ReportStats(
            income = income / 100.0,
            expense = expense / 100.0,
            balance = balance / 100.0,
            openingBalance = openingBalance / 100.0,
        )
    }

    data class ReportStats(
        val income: Double,
        val expense: Double,
        val balance: Double,
        val openingBalance: Double,
    )
}

private fun Entry.inScope(scope: ReportLedgerScope): Boolean = when (scope) {
    is ReportLedgerScope.Accounts ->
        account.type == AccountType.ASSET &&
            (scope.accountIds.isEmpty() || account.id in scope.accountIds)

    is ReportLedgerScope.Card ->
        account.type == AccountType.LIABILITY && account.id == scope.liabilityAccountId
}
