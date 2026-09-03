package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.form.TransactionForm

/**
 * Rewrites a transaction from an edited form.
 *
 * **The rewrite is total, and that is the rule this owns.**
 * `ITransactionRepository.updateTransaction` deletes every entry the transaction had and rebuilds it
 * from one leg plus its contra — so it can express an expense or an income and nothing else. A
 * transfer or a card payment has two monetary legs, and rebuilding from one of them would drop the
 * other without failing. `Transaction.editObstacle` is where that is decided, once — and what it
 * decides is what *this* form can express, not whether the user may edit at all: a transfer is
 * corrected through the transfer form and a partial card payment through the payment one, so the
 * view's own gate reads the label and is wider than this.
 *
 * The contra leg is carried through from the built intent and never defaulted: the rewrite has
 * already deleted the old entries, so a caller that omitted it would turn a one-sided intent into an
 * unbalanced write — refused at the boundary, with the edit rolled back and nothing to show for it.
 *
 * It answers the transaction as the ledger holds it **after** the rewrite, because the legs are new:
 * a caller that echoed back what it sent would be reporting its own request rather than what was
 * written.
 */
interface UpdateTransactionUseCase {

    /**
     * The transaction is resolved **when the operation runs**, so the gate reads the legs it has at
     * that instant rather than the ones a screen loaded earlier; an identity that matches nothing is
     * refused with `TransactionError.NOT_FOUND` and nothing is written.
     */
    suspend operator fun invoke(
        transactionId: Long,
        form: TransactionForm,
    ): Either<Throwable, Transaction>

    /**
     * The convenience for a caller that already holds the transaction. It extracts the identity and
     * delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(
        transaction: Transaction,
        form: TransactionForm,
    ): Either<Throwable, Transaction> = invoke(transaction.id, form)
}
