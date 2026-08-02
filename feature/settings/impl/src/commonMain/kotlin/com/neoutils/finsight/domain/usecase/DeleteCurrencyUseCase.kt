package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.neoutils.finsight.database.dao.AccountDao
import com.neoutils.finsight.database.dao.BudgetDao
import com.neoutils.finsight.domain.error.CurrencyError
import com.neoutils.finsight.domain.repository.ICurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository

/**
 * Deletes a currency — refused by whoever **denominates** it, and taking the rate archive
 * with it.
 *
 * An **account** or a **budget** blocks it, with the reason the user can act on: both
 * denominations are immutable, so deleting the currency would leave a number nobody can
 * name any more. Archiving is the way out, and the message says so.
 *
 * A **rate** does not block it: it is removed in the same write. That second half is what
 * makes the first safe. An observation left behind would go on being a **conversion
 * path** — the resolver reads the archive without consulting the offered set — producing
 * figures triangulated through a currency that exists nowhere in the interface; it could
 * still be opened for correction in a form whose selector does not contain it; and, codes
 * being reusable, deleting and re-registering an invented one would silently re-attach
 * the old observations to a different concept.
 *
 * The cost is destroying observations the user made, and the mitigation is [ratesToRemove]
 * saying **how many** before it happens instead of hiding it.
 */
class DeleteCurrencyUseCase(
    private val repository: ICurrencyRepository,
    private val exchangeRateRepository: IExchangeRateRepository,
    private val accountDao: AccountDao,
    private val budgetDao: BudgetDao,
) {
    /**
     * How many rate observations the deletion would take with it — what the confirmation
     * states before the user commits to it.
     */
    suspend fun ratesToRemove(code: String): Int =
        exchangeRateRepository.countNaming(code.uppercase())

    suspend operator fun invoke(code: String): Either<CurrencyError, Unit> {
        val normalized = code.uppercase()

        if (accountDao.countByCurrency(normalized) > 0) {
            return CurrencyError.DENOMINATED_BY_ACCOUNT.left()
        }

        if (budgetDao.countByCurrency(normalized) > 0) {
            return CurrencyError.DENOMINATED_BY_BUDGET.left()
        }

        // The same write: an observation must not outlive the currency it speaks about.
        exchangeRateRepository.removeAllNaming(normalized)
        repository.delete(normalized)

        return Unit.right()
    }
}
