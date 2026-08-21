package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.InstallmentError
import com.neoutils.finsight.domain.exception.InstallmentException
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.repository.IInstallmentRepository

class UpdateInstallmentUseCaseImpl(
    private val installmentRepository: IInstallmentRepository,
) : UpdateInstallmentUseCase {

    override suspend fun invoke(
        installmentId: Long,
        count: Int,
        totalAmount: Double,
    ): Either<Throwable, Installment> = either {
        // Updating is a blind `UPDATE` by id, which touches nothing when the id matches
        // nothing: without this the caller would be told the installment was corrected.
        val installment = ensureNotNull(
            catch { installmentRepository.getInstallmentById(installmentId) }.bind()
        ) {
            InstallmentException(InstallmentError.NotFound)
        }

        ensure(count > 0) { InstallmentException(InstallmentError.NonPositiveCount) }
        ensure(totalAmount > 0.0) { InstallmentException(InstallmentError.NonPositiveTotal) }

        catch {
            installmentRepository.updateInstallment(
                id = installmentId,
                count = count,
                totalAmount = totalAmount,
            )
        }.bind()

        installment.copy(count = count, totalAmount = totalAmount)
    }
}
