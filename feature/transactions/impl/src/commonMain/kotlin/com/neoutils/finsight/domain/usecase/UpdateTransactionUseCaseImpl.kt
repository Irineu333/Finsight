package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.flatMap
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.domain.repository.ITransactionRepository

/**
 * The composition itself — see [UpdateTransactionUseCase] for what it owns, and for
 * the single-monetary-leg restriction it inherits from the repository.
 *
 * `intent.legs.first()` is that restriction, applied: a rewrite takes one monetary
 * leg plus the contra the intent already carries. Passing the contra is not optional
 * — the rewrite deletes the old entries, so omitting it would turn a one-sided intent
 * into an unbalanced write, refused at the boundary with the edit silently rolled
 * back.
 *
 * The write is wrapped in `catch {}` for the same reason as in
 * [CreateTransactionUseCaseImpl]: the ledger's boundary throws its refusals, and
 * `either {}` intercepts a `Raise`, not an exception.
 */
class UpdateTransactionUseCaseImpl(
    private val buildTransactionUseCase: BuildTransactionUseCase,
    private val transactionRepository: ITransactionRepository,
) : UpdateTransactionUseCase {

    override suspend operator fun invoke(
        transactionId: Long,
        form: TransactionForm,
    ): Either<Throwable, Unit> = buildTransactionUseCase(form).flatMap { intent ->
        catch {
            transactionRepository.updateTransaction(
                id = transactionId,
                title = intent.title,
                date = intent.date,
                leg = intent.legs.first(),
                contra = intent.contra,
            )
        }
    }
}
