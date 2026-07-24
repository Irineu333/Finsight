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

    /** The account can be retired; [action] names archive-vs-delete as usual. */
    data class Retire(val action: RetireAction) : AccountRetireOffer

    /** The account is the default one, so no retire is offered — the screen guides
     *  the user to elect another default first. */
    data object UnavailableDefault : AccountRetireOffer
}

/**
 * The default account cannot be retired ([AccountRetireOffer.UnavailableDefault]);
 * any other account offers the shared archive-vs-delete decision from [retireActionOf].
 */
fun accountRetireOfferOf(hasMovement: Boolean, isDefault: Boolean): AccountRetireOffer =
    if (isDefault) {
        AccountRetireOffer.UnavailableDefault
    } else {
        AccountRetireOffer.Retire(retireActionOf(hasMovement))
    }
