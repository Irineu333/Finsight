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
 * What names a currency: what refuses its deletion, and what a deletion would take.
 *
 * A **rate** is here without blocking anything, on purpose: it is what the confirmation
 * has to be able to say a number about.
 */
data class CurrencyUsage(
    val accounts: Int,
    val budgets: Int,
    val rates: Int,
) {
    /** Nothing denominates it, so it may be deleted rather than archived. */
    val isDeletable: Boolean get() = accounts == 0 && budgets == 0
}

/**
 * Deletes a currency — refused by whoever **denominates** it, and taking the rate archive
 * with it.
 *
 * An **account** or a **budget** blocks it, with the reason the user can act on: both
 * denominations are immutable, so deleting the currency would leave a number nobody can
 * name any more. Archiving is the way out, and the message says so.
 *
 * A **rate** does not block it: it is removed in the same write, which
 * [ICurrencyRepository.delete] owns — one transaction, because half of that pair is a
 * state nothing in the app can read. That second half is what makes the first safe. An
 * observation left behind would go on being a **conversion path** — the resolver reads
 * the archive without consulting the offered set — producing figures triangulated through
 * a currency that exists nowhere in the interface; it could still be opened for
 * correction in a form whose selector does not contain it; and, codes being reusable,
 * deleting and re-registering an invented one would silently re-attach the old
 * observations to a different concept.
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
     * What names this currency today — the single answer both the refusal and the screen
     * read.
     *
     * The screen needs it to say, *before* the user reaches for the action, that deleting
     * is refused and how many observations a deletion would take. Deriving that a second
     * time in the UI is how a screen ends up offering a delete the use case refuses.
     */
    suspend fun usageOf(code: String): CurrencyUsage {
        val normalized = code.uppercase()

        return CurrencyUsage(
            accounts = accountDao.countByCurrency(normalized),
            budgets = budgetDao.countByCurrency(normalized),
            rates = exchangeRateRepository.countNaming(normalized),
        )
    }

    suspend operator fun invoke(code: String): Either<CurrencyError, Unit> {
        val normalized = code.uppercase()
        val usage = usageOf(normalized)

        if (usage.accounts > 0) return CurrencyError.DENOMINATED_BY_ACCOUNT.left()

        if (usage.budgets > 0) return CurrencyError.DENOMINATED_BY_BUDGET.left()

        // The rate archive goes with it, in the same write — which is the repository's
        // promise and not two calls from here. An observation must not outlive the
        // currency it speaks about, and this is the decision that it may go, not the
        // orchestration of how.
        repository.delete(normalized)

        return Unit.right()
    }
}
