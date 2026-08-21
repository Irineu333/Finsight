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
     * The canonical form, and the one that carries the implementation.
     *
     * The transaction is resolved **when the operation runs**, so an identity that
     * matches nothing is refused with `TransactionError.NOT_FOUND` and nothing is
     * removed — rather than reaching the repository, which deletes by id and cannot
     * tell "already gone" from "never existed".
     */
    suspend operator fun invoke(transactionId: Long): Either<Throwable, Unit>

    /**
     * The convenience for a caller that already holds the transaction. It extracts
     * the identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(transaction: Transaction): Either<Throwable, Unit> =
        invoke(transaction.id)
}
