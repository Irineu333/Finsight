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
    suspend operator fun invoke(transaction: Transaction): Either<Throwable, Unit>
}
