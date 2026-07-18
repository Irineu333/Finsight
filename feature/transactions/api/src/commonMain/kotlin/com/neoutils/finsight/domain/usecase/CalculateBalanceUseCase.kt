package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.repository.IEntryRepository
import kotlinx.datetime.YearMonth

class CalculateBalanceUseCase(
    private val entryRepository: IEntryRepository,
) {
    // Ledger-backed: Σ entries of the account (or all ASSET accounts) up to the target
    // month. The legacy in-memory form (CAP-2) is gone (task 4.3).
    suspend operator fun invoke(
        target: YearMonth,
        accountId: Long? = null,
    ): Double {
        return entryRepository.balanceUpTo(target = target, accountId = accountId)
    }
}
