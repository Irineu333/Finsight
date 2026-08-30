package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Transaction

/**
 * Removes a transaction and its ledger legs.
 *
 * The invariant that decides *whether* it may go — a paid invoice is immutable,
 * a closed one only accepts its own payment — lives at the write boundary, not
 * here. This exists so the removal has a layer: the ViewModels called the
 * repository directly, so a failure had nowhere to be reported.
 */
interface DeleteTransactionUseCase {

    /**
     * @param withoutCopy the person was told no copy of what this destroys could be kept
     * and said to go on anyway. It is a flag this layer interprets, never a screen naming
     * a different action: the removal is the same one either way, and which actions are
     * worth a copy is decided in the domain.
     *
     * A copy that was owed and could not be taken refuses by throwing
     * `PreventiveCaptureException`, which leaves the transaction where it is: only the
     * person told about it may say to come back [withoutCopy].
     */
    suspend operator fun invoke(
        transaction: Transaction,
        withoutCopy: Boolean = false,
    ): Either<Throwable, Unit>
}
