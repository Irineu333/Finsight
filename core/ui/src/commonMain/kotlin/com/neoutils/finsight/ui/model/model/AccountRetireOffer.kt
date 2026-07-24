package com.neoutils.finsight.ui.model

/**
 * How a screen offers retiring an **account** — the account-only wrapper around the
 * shared [RetireAction].
 *
 * An account has a third case the card and category never do: it may be the *default*
 * one, which the domain refuses to retire (`AccountError.CANNOT_ARCHIVE_DEFAULT` /
 * `CANNOT_DELETE_DEFAULT`). Rather than force a meaningless third [RetireAction]
 * member — with a dummy label/icon that would break the exhaustive `when`s in the
 * card and category screens — the extra case lives here, in a type only `AccountUi`
 * and the accounts screen consume. The DELETE-vs-ARCHIVE decision stays owned by
 * [retireActionOf], untouched and still shared by all three facades.
 */
sealed interface AccountRetireOffer {

    /** The [RetireAction] this offer names — archive-vs-delete as usual. Present in
     *  both cases so the screen has a label and icon to show even when disabled. */
    val action: RetireAction

    /** The account can be retired: the screen shows the action enabled. */
    data class Retire(override val action: RetireAction) : AccountRetireOffer

    /** The account is the default one, so it cannot be retired: the screen shows the
     *  same action but disabled. The domain refuses it too (CANNOT_ARCHIVE_DEFAULT). */
    data class UnavailableDefault(override val action: RetireAction) : AccountRetireOffer
}

/**
 * The default account cannot be retired ([AccountRetireOffer.UnavailableDefault]);
 * any other account offers the shared archive-vs-delete decision from [retireActionOf].
 * The archive-vs-delete choice itself is the same either way — being the default only
 * decides whether the offer is available.
 */
fun accountRetireOfferOf(hasMovement: Boolean, isDefault: Boolean): AccountRetireOffer {
    val action = retireActionOf(hasMovement)
    return if (isDefault) {
        AccountRetireOffer.UnavailableDefault(action)
    } else {
        AccountRetireOffer.Retire(action)
    }
}
