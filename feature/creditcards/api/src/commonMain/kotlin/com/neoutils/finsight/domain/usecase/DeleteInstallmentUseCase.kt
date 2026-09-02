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
     *
     * @param withoutCopy the person was told no copy of the N transactions this destroys
     * could be kept and said to go on anyway. It is a flag this layer interprets, never a
     * screen naming a different action.
     *
     * A copy that was owed and could not be taken refuses by throwing
     * `PreventiveCaptureException`, which leaves every instalment where it is: only the
     * person told about it may say to come back [withoutCopy].
     */
    suspend operator fun invoke(
        installmentId: Long,
        withoutCopy: Boolean = false,
    ): Either<Throwable, Unit>

    /**
     * The convenience for a caller that already holds the installment. It extracts the
     * identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(
        installment: Installment,
        withoutCopy: Boolean = false,
    ): Either<Throwable, Unit> = invoke(
        installmentId = installment.id,
        withoutCopy = withoutCopy,
    )
}
