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
     *
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
        transactionId: Long,
        withoutCopy: Boolean = false,
    ): Either<Throwable, Unit>

    /**
     * The convenience for a caller that already holds the transaction. It extracts
     * the identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(
        transaction: Transaction,
        withoutCopy: Boolean = false,
    ): Either<Throwable, Unit> = invoke(
        transactionId = transaction.id,
        withoutCopy = withoutCopy,
    )
}
