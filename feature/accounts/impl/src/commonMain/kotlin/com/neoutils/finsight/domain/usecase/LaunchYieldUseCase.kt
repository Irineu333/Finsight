package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.datetime.LocalDate

/**
 * Records a yield: an ordinary income transaction on the account, its counterpart on
 * the nominal income account, carrying the yield category's dimension.
 *
 * It is a **launch**, not an adjustment. Every call writes a new transaction; nothing
 * is recognised, rewritten or accumulated onto. That is not an omission — an
 * adjustment pursues a target balance and therefore has to find its predecessor,
 * which it does by the shape *transaction on this date, on this account, with an
 * EQUITY counter-leg*. `ASSET ← INCOME` is exactly the shape of an ordinary income,
 * and the premise here is the current account where the salary lands the same day: a
 * use case that recognised its predecessor would sooner or later rewrite the salary
 * (design D1).
 *
 * The consequence is that adjusting a balance stays untouched, and that a yield is
 * derived as income by everything that classifies a transaction by the natures of its
 * accounts — without any of it knowing that yield exists.
 */
class LaunchYieldUseCase(
    private val accountRepository: IAccountRepository,
    private val transactionRepository: ITransactionRepository,
    private val ensureYieldCategory: EnsureYieldCategoryUseCase,
) {
    /**
     * The canonical form, and the one that carries the implementation.
     *
     * The account is resolved **when the operation runs**; an identity that matches
     * nothing is refused with `AccountError.NOT_FOUND` and nothing is written.
     */
    suspend operator fun invoke(
        accountId: Long,
        date: LocalDate,
        amount: Double,
    ): Either<Throwable, Unit> = either {
        ensureNotNull(catch { accountRepository.getAccountById(accountId) }.bind()) {
            AccountException(AccountError.NOT_FOUND)
        }

        catch {
            val category = ensureYieldCategory()

            transactionRepository.createTransaction(
                TransactionIntent(
                    title = null,
                    date = date,
                    legs = listOf(
                        TransactionLeg(
                            type = TransactionType.INCOME,
                            amount = amount,
                            accountId = accountId,
                        )
                    ),
                    // Income, and classified by the yield category's dimension — the
                    // one thing that tells it apart from any other income later on.
                    contra = ContraLeg(
                        nature = AccountType.INCOME,
                        dimensionId = category.dimensionId,
                    ),
                )
            )
        }.bind()
    }

    /**
     * The convenience for a caller that already holds the account. It extracts the
     * identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(
        account: Account,
        date: LocalDate,
        amount: Double,
    ): Either<Throwable, Unit> = invoke(
        accountId = account.id,
        date = date,
        amount = amount,
    )
}
