package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.TransactionError
import com.neoutils.finsight.domain.exception.TransactionException
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.extension.editObstacle

class UpdateTransactionUseCaseImpl(
    private val transactionRepository: ITransactionRepository,
    private val buildTransaction: BuildTransactionUseCase,
) : UpdateTransactionUseCase {

    override suspend fun invoke(
        transactionId: Long,
        form: TransactionForm,
    ): Either<Throwable, Transaction> = either {
        // Resolved before anything is built, so the gate below reads the legs the
        // transaction has now: `updateTransaction` writes by id and would rewrite nothing,
        // quietly, for an identity that matches none.
        val stored = ensureNotNull(
            catch { transactionRepository.getTransactionById(transactionId) }.bind(),
        ) {
            TransactionException(TransactionError.NOT_FOUND)
        }

        // The one rule this operation owns, decided by `Transaction.editObstacle` and read
        // here rather than restated: the rewrite deletes every old entry and rebuilds from a
        // single leg, so a transaction it cannot express is refused instead of half-written.
        stored.editObstacle?.let { raise(TransactionException(it)) }

        val intent = buildTransaction(form).bind()

        catch {
            transactionRepository.updateTransaction(
                id = transactionId,
                title = intent.title,
                date = intent.date,
                // The intent of a form has exactly one leg, and the contra travels with it:
                // without the contra the rewrite is a one-sided write the boundary refuses,
                // after the old entries are already gone.
                leg = intent.legs.first(),
                contra = intent.contra,
            )
        }.bind()

        // Read back rather than echoed: the legs are new ones, and a caller told what it sent
        // would be reporting its own request instead of what the ledger now holds.
        ensureNotNull(catch { transactionRepository.getTransactionById(transactionId) }.bind()) {
            TransactionException(TransactionError.NOT_FOUND)
        }
    }
}
