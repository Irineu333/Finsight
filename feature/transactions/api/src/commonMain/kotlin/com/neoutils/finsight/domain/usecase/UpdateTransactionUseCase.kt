package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.form.TransactionForm

/**
 * Rewrites an existing transaction from an edited form.
 *
 * Like [CreateTransactionUseCase], it is the composition that had no owner: the form
 * becomes an intent, and the intent replaces the transaction's row and its ledger
 * legs, as one write.
 *
 * ⚠️ **Only a transaction with a single monetary leg may be rewritten** — an expense
 * or an income. The rewrite deletes every old entry and rebuilds from the one the
 * form describes, so routing a transfer or a card payment, which have two monetary
 * legs, through here would drop the second silently. The restriction is
 * `ITransactionRepository.updateTransaction`'s, repeated here because this is the
 * boundary a consumer sees; the screens honour it through
 * `ViewTransactionUiState.isEditable`.
 *
 * **What a consumer may change is its own decision, not this contract's.** Editing
 * the *money* of a transaction — its amount or the account it posts to — is
 * expressible here because the app's edit screen offers it, but a consumer is free
 * to offer less: the MCP surface, for one, offers only category, description and
 * date, because changing the money of a transaction is removing it and creating
 * another, and an edit that disguised that would hide the correction. Deciding
 * *whether* an operation is offered is a consumer's call; deciding what it *is* is
 * this use case's.
 *
 * **Public contract.** An identifier and a [TransactionForm] in, `Unit` out. Failures
 * arrive as `Throwable`, carrying the domain's own error types with their English
 * `message`; no presentation type crosses this boundary, `UiText` included.
 *
 * An interface with an implementation in the `impl`, because building the intent
 * depends on use cases that stay internal to their features.
 */
interface UpdateTransactionUseCase {
    suspend operator fun invoke(
        transactionId: Long,
        form: TransactionForm,
    ): Either<Throwable, Unit>
}
