package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.repository.IEntryRepository
import kotlinx.datetime.YearMonth

/**
 * `Σ entries` up to a month — of one account, or of every `ASSET` account.
 *
 * The two are **different reads with different shapes**, and saying so is the point.
 * One account is one currency, so its balance is a number and the caller already knows
 * what denominates it. Every account is however many currencies the user has, so that
 * balance is a [MoneyByCurrency] — and it is the read the dashboard's total comes
 * through, which makes it the door multi-currency enters the app by (design D8).
 *
 * Two members rather than one nullable parameter: with a `Long?` the caller of the
 * spanning read is one forgotten argument away from the scoped one, and the compiler
 * would have nothing to say about it.
 */
class CalculateBalanceUseCase(
    private val entryRepository: IEntryRepository,
) {
    /** Every `ASSET` account up to [target], per currency. Nothing here is converted. */
    suspend operator fun invoke(target: YearMonth): MoneyByCurrency {
        return entryRepository.balanceUpToByCurrency(target = target)
    }

    /** One account up to [target] — scalar, denominated by the account itself. */
    suspend fun forAccount(accountId: Long, target: YearMonth): Double {
        return entryRepository.accountBalanceUpTo(accountId = accountId, target = target)
    }
}
