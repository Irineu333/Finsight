package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.form.TransactionForm

/**
 * Records what the user filled in: the form becomes the intent the ledger can write,
 * and the intent becomes a transaction.
 *
 * The two steps had no owner between them — every caller composed
 * [BuildTransactionUseCase] with the repository's write itself, which is one
 * composition too many for something every consumer of this feature needs. This is
 * that composition, named once.
 *
 * It is deliberately *only* the composition: what a valid form is stays with
 * [ValidateTransactionFormUseCase], what the invoice of a card purchase is stays with
 * the credit-card feature, and balancing the legs stays at the ledger's write
 * boundary. Nothing about the transaction is decided here.
 *
 * **Public contract.** A [TransactionForm] in, the written [Transaction] out — with
 * the label the ledger derived, never one the caller declared. Failures arrive as
 * `Throwable`, carrying the domain's own error types with their English `message`; no
 * presentation type crosses this boundary, `UiText` included.
 *
 * An interface with an implementation in the `impl`, because building the intent
 * depends on use cases that stay internal to their features.
 */
interface CreateTransactionUseCase {
    suspend operator fun invoke(form: TransactionForm): Either<Throwable, Transaction>
}
