package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction

/**
 * The point of view under which an operation is presented: the account (the card
 * enters via `CreditCard.accountId`) whose leg the screen shows. A single data
 * class — the sealed `Account`/`Card` split existed only because the legacy leg
 * had two forms; an `Entry` has one.
 */
data class TransactionPerspective(
    val accountId: Long,
    val invoiceId: Long? = null,
) {
    /**
     * Resolves the legacy leg this perspective points at, matching by account id.
     * The card-only perspective is unused in production; this legacy match
     * dissolves with §6.9 once the leg model is gone.
     */
    fun resolve(operation: Operation): Transaction? =
        operation.transactions.firstOrNull { it.target.isAccount && it.account?.id == accountId }
}
