package com.neoutils.finsight.extension

import com.neoutils.finsight.domain.error.TransactionError
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionLabel

/**
 * What stops this transaction from being rewritten **through the transaction form**, or `null`
 * when nothing does.
 *
 * **The rewrite is a shape before it is a permission.** The form states a *single* monetary leg,
 * and `UpdateTransactionUseCase` rebuilds the whole transaction from it plus a contra leg,
 * deleting every old entry first. That expresses an expense or an income exactly, and it expresses
 * nothing else — so what is written here is not a policy somebody chose, it is the set of
 * transactions this operation is incapable of describing.
 *
 * - **More than one monetary leg.** A transfer and a card payment have two ends. Rebuilding from
 *   one of them drops the other silently, and the money it moved simply stops existing.
 * - **An adjustment.** Its contra leg is `EQUITY` — the user's own reconciliation — and the form
 *   builds an ordinary nominal one, so a rewrite would quietly turn a correction into spending.
 * - **One share of an installment.** The plan keeps the count and the total the user declared, and
 *   neither is derived from the transactions; editing one share alone leaves the plan describing
 *   money that is no longer there.
 *
 * **This is not the question "may the user edit this at all".** `ITransactionRepository.updateTransaction`
 * takes a list of legs and can rewrite a transfer or a card payment; what cannot express them is
 * *this* form. A transfer is corrected through the transfer form (`UpdateTransferUseCase`) and a
 * partial payment through the payment one (`UpdateAdvanceInvoicePaymentUseCase`), which is why the
 * view's own gate decides **by label** which form to open and is wider than this. Reading this one
 * as the screen's gate would withdraw two actions that have somewhere to go.
 *
 * What is **not** here is the archived account or card: refusing that is the write boundary's, which
 * already guards both sides of the rewrite (`closedLegBlockingChange`), and restating it would be
 * a second copy of that rule.
 */
val Transaction.editObstacle: TransactionError?
    get() = when {
        // Read before the label, because the label of a transfer or a payment is never
        // ADJUSTMENT and this is the more precise thing to say about either of them.
        monetaryEntries.size > 1 -> TransactionError.MULTIPLE_MONETARY_LEGS
        label == TransactionLabel.ADJUSTMENT -> TransactionError.IS_ADJUSTMENT
        installmentId != null -> TransactionError.INSTALLMENT_SHARE
        else -> null
    }
