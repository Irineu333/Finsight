package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.util.AppIcon

/**
 * Which icon a **new** account form pre-selects.
 *
 * The icon exists to tell one account from another at a glance, so a form that
 * always opens on the same constant defeats it: whoever creates accounts without
 * touching the picker ends up with a list of identical rows. The answer here is
 * the first icon of the account catalog (`FeatureIconCatalog.accounts`) that no
 * **open** account already uses, which makes the catalog's order part of the
 * observable behaviour — reordering it changes what is suggested.
 *
 * "In use" means an open account. An archived one appears in no active listing
 * and no selector, so its icon competes with nothing and becomes suggestible
 * again; the accepted consequence is that reopening an account may recreate a
 * collision. The comparison is by the persisted `iconKey`, not by enum identity,
 * so an unknown key in the database eliminates no catalog entry.
 *
 * With the whole catalog in use the answer is [AppIcon.WALLET]. The suggestion is
 * a convenience and never a guarantee of uniqueness: the user may pick any icon
 * the selector offers, an already used one included, and nothing is blocked,
 * hidden or refused because of it.
 *
 * There is no failure case — hence a plain [AppIcon] rather than an `Either`: the
 * exhausted catalog has a defined outcome, and a screen would have nothing to do
 * about a suggestion that failed.
 */
interface SuggestAccountIconUseCase {
    suspend operator fun invoke(): AppIcon
}
