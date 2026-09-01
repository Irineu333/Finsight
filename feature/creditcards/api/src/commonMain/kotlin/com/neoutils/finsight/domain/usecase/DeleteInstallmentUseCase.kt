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

    /**
     * @param withoutCopy the person was told no copy of the N transactions this destroys
     * could be kept and said to go on anyway. It is a flag this layer interprets, never a
     * screen naming a different action.
     *
     * A copy that was owed and could not be taken refuses by throwing
     * `PreventiveCaptureException`, which leaves every instalment where it is: only the
     * person told about it may say to come back [withoutCopy].
     */
    suspend operator fun invoke(
        installment: Installment,
        transactions: List<Transaction>,
        withoutCopy: Boolean = false,
    ): Either<Throwable, Unit>
}
