package com.neoutils.finsight.feature.transactions.api

/**
 * What the screen that opens the transaction form already has in front of the user.
 *
 * One type rather than two nullables: an account *and* a card at once means nothing, and the shape
 * that admits it would leave the form to decide which of the two it believed. Only the id travels,
 * so a screen hands over what it is looking at without any model of an `impl` crossing the
 * boundary.
 *
 * It is the item in **focus now**, never the one the route named. A route carries the initial
 * selection; the focus moves while the screen lives, and a form filled from the route would state
 * the card the user opened rather than the one they are looking at.
 */
sealed interface TransactionOrigin {

    data class Account(val accountId: Long) : TransactionOrigin

    data class CreditCard(val creditCardId: Long) : TransactionOrigin
}
