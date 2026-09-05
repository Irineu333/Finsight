package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Installment

/**
 * Corrects what an installment says about itself: how many shares it has and the total
 * the user declared.
 *
 * Neither is derived. `count` and `totalAmount` are copies of facts about the
 * installment's transactions, kept because the per-share rounding means Σ of the shares
 * need not equal the declared total — R$ 100,00 in three is 3 × 33,33 — so the total is
 * the user's word and not a sum. Writing them is therefore an edit, not a
 * recalculation, and it moves no money: the transactions already in the ledger are left
 * exactly as they are.
 */
interface UpdateInstallmentUseCase {

    /**
     * The installment is resolved **when the operation runs**; an identity that matches
     * nothing is refused with `InstallmentError.NotFound` and nothing is written.
     *
     * It answers the installment as stored, so a caller can report what it changed.
     *
     * @param count how many shares the installment has — at least one, since an
     * installment of none describes nothing.
     * @param totalAmount the total the user declared, which must be positive.
     */
    suspend operator fun invoke(
        installmentId: Long,
        count: Int,
        totalAmount: Double,
    ): Either<Throwable, Installment>

    /**
     * The convenience for a caller that already holds the installment. It extracts the
     * identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(
        installment: Installment,
        count: Int,
        totalAmount: Double,
    ): Either<Throwable, Installment> = invoke(installment.id, count, totalAmount)
}
