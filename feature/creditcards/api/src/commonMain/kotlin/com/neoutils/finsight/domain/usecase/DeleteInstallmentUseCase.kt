package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Installment

/**
 * Removes an installment and every transaction that belongs to it.
 *
 * One decision by the user, one unit of work: deleting some of the instalments
 * and failing halfway would leave an installment describing money that is still
 * in the ledger, or the other way round.
 */
interface DeleteInstallmentUseCase {

    /**
     * The canonical form, and the one that carries the implementation.
     *
     * The installment and the transactions that name it are resolved **when the
     * operation runs**, so what is removed is what points at the installment at that
     * moment and not what a screen listed earlier. An identity that matches nothing is
     * refused with `InstallmentError.NotFound`, and nothing is removed.
     */
    suspend operator fun invoke(installmentId: Long): Either<Throwable, Unit>

    /**
     * The convenience for a caller that already holds the installment. It extracts the
     * identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(installment: Installment): Either<Throwable, Unit> =
        invoke(installment.id)
}
