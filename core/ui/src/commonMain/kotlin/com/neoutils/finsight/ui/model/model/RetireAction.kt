package com.neoutils.finsight.ui.model

/**
 * How a screen should offer retiring an account, a card or a category.
 *
 * The *outcome* is the ledger's decision and belongs to `CloseAccountUseCase`:
 * an account with movement cannot be removed without breaking the entries that
 * reference it. What a screen may decide is how to **name** and present that —
 * "excluir" promises removal, and promising it for something that will be closed
 * is what made the modals lie. This is that presentation rule, in one place, for
 * accounts and cards alike.
 */
enum class RetireAction {
    DELETE,
    CLOSE,
}

/** Maps the ledger fact to the action a screen offers. */
fun retireActionOf(hasMovement: Boolean): RetireAction =
    if (hasMovement) RetireAction.CLOSE else RetireAction.DELETE
