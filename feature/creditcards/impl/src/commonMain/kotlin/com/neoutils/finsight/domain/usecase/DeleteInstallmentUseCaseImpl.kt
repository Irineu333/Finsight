package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.database.dao.InstallmentDao
import com.neoutils.finsight.domain.error.InstallmentError
import com.neoutils.finsight.domain.exception.InstallmentException
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository

class DeleteInstallmentUseCaseImpl(
    private val transactionRepository: ITransactionRepository,
    private val installmentRepository: IInstallmentRepository,
    // The installment's own question about the transactions that name it, asked where
    // the facade already asks the others (see `InstallmentDao`).
    private val installmentDao: InstallmentDao,
) : DeleteInstallmentUseCase {

    override suspend fun invoke(installmentId: Long): Either<Throwable, Unit> = either {
        ensureNotNull(
            catch { installmentRepository.getInstallmentById(installmentId) }.bind()
        ) {
            InstallmentException(InstallmentError.NotFound)
        }

        catch {
            transactionRepository.deleteTransactionsByIds(
                installmentDao.transactionIds(installmentId)
            )
            installmentRepository.deleteInstallmentById(installmentId)
        }.bind()
    }
}
