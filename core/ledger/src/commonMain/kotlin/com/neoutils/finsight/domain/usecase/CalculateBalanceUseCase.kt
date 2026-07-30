package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CurrencyBalance
import com.neoutils.finsight.domain.repository.AccountBalance
import com.neoutils.finsight.domain.repository.IEntryRepository
import kotlinx.datetime.YearMonth

/**
 * Σ entries up to the target month, of one account or of every ASSET account.
 *
 * The two are different reads, and they no longer share a signature: naming an account
 * gives one number in that account's currency, while naming none spans every account the
 * user has, so the figure comes back **per currency**. The nullable parameter hid that
 * behind a single `Double`, which is exactly where "which currency is this?" stopped having
 * an answer at the call site.
 */
class CalculateBalanceUseCase(
    private val entryRepository: IEntryRepository,
) {
    suspend operator fun invoke(target: YearMonth, accountId: Long): AccountBalance =
        entryRepository.balanceUpTo(target = target, accountId = accountId)

    suspend operator fun invoke(target: YearMonth): CurrencyBalance =
        entryRepository.naturalBalanceUpTo(target = target, type = AccountType.ASSET)
}
