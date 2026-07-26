package com.neoutils.finsight.ui.model

import com.neoutils.finsight.extension.DisplayAmount

/**
 * A flat, display-ready view of an account's period figures. Carries no domain
 * graph and does no calculation — every value is derived from the ledger by the
 * ViewModel and handed in already computed. The domain [Account] itself (for the
 * icon, name and actions) is rendered by the component that receives it directly.
 *
 * Every figure arrives with its sign policy already resolved, so the card only renders:
 * before, the difference between [income] and [expense] existed nowhere but in the
 * composable that read them, and a second screen showing the same account could
 * disagree with the first.
 */
data class AccountUi(
    val id: Long,
    val openingBalance: DisplayAmount,
    val balance: DisplayAmount,
    val income: DisplayAmount,
    val expense: DisplayAmount,
    val adjustment: DisplayAmount,
    val settlement: DisplayAmount,
    // Whether the account has any ledger movement. The ledger decides whether it
    // can be removed; this is only the fact the screen needs to name the action.
    val hasMovement: Boolean = false,
    // The default account cannot be retired at all — a third case of the offer.
    val isDefault: Boolean = false,
) {
    val retireOffer: AccountRetireOffer get() = accountRetireOfferOf(hasMovement, isDefault)
}
