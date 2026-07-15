package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.extension.signedCents
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth

class CalculateBalanceUseCase(
    private val entryRepository: IEntryRepository,
) {
    // In-memory form used by screens that already hold the transaction list.
    // Kept transaction-based during coexistence; equals the ledger figure.
    operator fun invoke(
        target: YearMonth,
        transactions: List<Transaction>,
        accountId: Long? = null
    ): Double {
        return transactions
            .filter { it.date.yearMonth <= target }
            .filter { it.target.isAccount }
            .filter { accountId == null || it.account?.id == accountId }
            .sumOf { it.signedCents() } / 100.0
    }

    // Ledger-backed form: Σ entries of the account up to the target month.
    suspend operator fun invoke(
        target: YearMonth,
        accountId: Long? = null,
    ): Double {
        return entryRepository.balanceUpTo(target = target, accountId = accountId)
    }
}
