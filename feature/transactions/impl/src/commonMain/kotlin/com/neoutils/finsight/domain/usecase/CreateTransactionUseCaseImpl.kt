package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.flatMap
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.domain.repository.ITransactionRepository

/**
 * The composition itself — see [CreateTransactionUseCase] for what it owns and what
 * it deliberately does not.
 *
 * The write is wrapped in `catch {}` because the ledger's boundary *throws* its
 * refusals (a closed account, an unbalanced intent) and `either {}` intercepts a
 * `Raise`, not an exception. Untyped, they escaped the `Either` and reached the
 * caller as a crash.
 */
class CreateTransactionUseCaseImpl(
    private val buildTransactionUseCase: BuildTransactionUseCase,
    private val transactionRepository: ITransactionRepository,
) : CreateTransactionUseCase {

    override suspend operator fun invoke(
        form: TransactionForm,
    ): Either<Throwable, Transaction> = buildTransactionUseCase(form).flatMap { intent ->
        catch { transactionRepository.createTransaction(intent) }
    }
}
