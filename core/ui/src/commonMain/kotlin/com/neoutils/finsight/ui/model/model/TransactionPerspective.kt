package com.neoutils.finsight.ui.model

/**
 * The point of view under which an transaction is presented: the account (the card
 * enters via `CreditCard.accountId`) whose leg the screen shows. A single data
 * class — the sealed `Account`/`Card` split existed only because the legacy leg
 * had two forms; an `Entry` has one.
 */
data class TransactionPerspective(
    val accountId: Long,
    val invoiceId: Long? = null,
)
