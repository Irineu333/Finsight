package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Transaction

/**
 * Removes an installment and every transaction that belongs to it.
 *
 * One decision by the user, one unit of work: deleting some of the instalments
 * and failing halfway would leave an installment describing money that is still
 * in the ledger, or the other way round.
 */
interface DeleteInstallmentUseCase {
    suspend operator fun invoke(
        installment: Installment,
        transactions: List<Transaction>,
    ): Either<Throwable, Unit>
}
