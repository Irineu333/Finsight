package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.TransactionError
import com.neoutils.finsight.domain.exception.TransactionException
import com.neoutils.finsight.domain.ledger.RemovalAnnouncement
import com.neoutils.finsight.domain.ledger.WithheldAnnouncement
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.feature.backup.api.PreventiveCaptureException

class DeleteTransactionUseCaseImpl(
    private val transactionRepository: ITransactionRepository,
) : DeleteTransactionUseCase {

    // The person was already offered the copy and said to go on without it, so the
    // announcement has been answered before the ledger is asked anything.
    @OptIn(WithheldAnnouncement::class)
    override suspend fun invoke(
        transactionId: Long,
        withoutCopy: Boolean,
    ): Either<Throwable, Unit> = either {
        // Resolved before the removal so that an identity matching nothing is a refusal
        // and not a silent success: `deleteTransactionById` removes by id, and asking it
        // to remove a row that is not there does exactly nothing, quietly.
        ensureNotNull(catch { transactionRepository.getTransactionById(transactionId) }.bind()) {
            TransactionException(TransactionError.NOT_FOUND)
        }

        catch {
            // The removal is the same one either way; what the answer changes is whether
            // the copy owed before it is still asked for (design D7).
            transactionRepository.deleteTransactionById(
                id = transactionId,
                announcement = if (withoutCopy) {
                    RemovalAnnouncement.Withheld
                } else {
                    RemovalAnnouncement.Announced
                },
            )
        }.onLeft { cause ->
            // A copy that could not be taken is not a failure to report and log: it is a
            // question, and the only answer to it is the person's. Left inside the
            // `Either` it would reach them as "something went wrong", which is what a
            // screen says when there is nothing to decide.
            if (cause is PreventiveCaptureException) throw cause
        }.bind()
    }
}
