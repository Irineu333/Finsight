package com.neoutils.finsight.domain.vault

import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.feature.backup.api.VaultOffer
import com.neoutils.finsight.feature.backup.api.VaultOfferTerms

/**
 * The offer, standing beside every destructive confirmation for as long as the vault is
 * off.
 *
 * **What a refusal costs is the tick, never the offer.** The switch lives on a screen
 * somebody has to go looking for, so a confirmation is the only place the vault is met by
 * the people who need it most; an offer that withdrew after one no would leave them with no
 * way in at all. What it does instead is arrive already answered — unticked, and worded as
 * the reminder it is — which is present without being insistence.
 *
 * Both halves are the vault's own state and neither is a screen's: [VaultState.isOfferable]
 * is whether there is anything left to turn on, and [VaultState.wasDeclined] is the answer
 * given last time. A confirmation reads neither.
 *
 * Accepting turns the whole vault on (design D1). There is nothing narrower to turn on:
 * the switch governs every trigger, and an offer that armed one of them would be promising
 * something the vault cannot be. It goes through [VaultSwitch] rather than writing the
 * preference here, because turning the vault on is also what takes the first copy — and a
 * confirmation is the one place where that copy is the whole point.
 */
class StandingVaultOffer(
    private val vault: BackupVaultRepository,
    private val switch: VaultSwitch,
) : VaultOffer {

    override fun offer(): VaultOfferTerms? {
        val state = vault.observe().value
        if (!state.isOfferable) return null

        return VaultOfferTerms(
            intervalLabel = VaultInterval.nearest(state.interval).label,
            wasDeclined = state.wasDeclined,
            // Recorded when the action goes past the unticked box, never when the box is
            // shown: a sheet that was read and cancelled carries no answer.
            decline = vault::markDeclined,
            turnOn = { switch.setOn(true) },
        )
    }
}
