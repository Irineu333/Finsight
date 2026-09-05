package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.database.dao.InstallmentDao
import com.neoutils.finsight.domain.error.InstallmentError
import com.neoutils.finsight.domain.exception.InstallmentException
import com.neoutils.finsight.domain.ledger.RemovalAnnouncement
import com.neoutils.finsight.domain.ledger.WithheldAnnouncement
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.feature.backup.api.PreventiveCaptureException

class DeleteInstallmentUseCaseImpl(
    private val transactionRepository: ITransactionRepository,
    private val installmentRepository: IInstallmentRepository,
    // The installment's own question about the transactions that name it, asked where
    // the facade already asks the others (see `InstallmentDao`).
    private val installmentDao: InstallmentDao,
) : DeleteInstallmentUseCase {

    // The person was already offered the copy and said to go on without it, so the
    // announcement has been answered before the ledger is asked anything.
    @OptIn(WithheldAnnouncement::class)
    override suspend fun invoke(
        installmentId: Long,
        withoutCopy: Boolean,
    ): Either<Throwable, Unit> = either {
        ensureNotNull(
            catch { installmentRepository.getInstallmentById(installmentId) }.bind()
        ) {
            InstallmentException(InstallmentError.NotFound)
        }

        catch {
            // The batch is one removal and the copy owed before it is one copy, which is
            // why the answer is given once here and not per instalment.
            transactionRepository.deleteTransactionsByIds(
                ids = installmentDao.transactionIds(installmentId),
                announcement = if (withoutCopy) {
                    RemovalAnnouncement.Withheld
                } else {
                    RemovalAnnouncement.Announced
                },
            )
            installmentRepository.deleteInstallmentById(installmentId)
        }.onLeft { cause ->
            // A copy that could not be taken is a question for the person, not an error to
            // log — and until they answer it, all N instalments are still there.
            if (cause is PreventiveCaptureException) throw cause
        }.bind()
    }
}
