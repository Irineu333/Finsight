package com.neoutils.finsight.domain.vault

import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.feature.backup.api.VaultOffer
import com.neoutils.finsight.feature.backup.api.VaultOfferTerms

/**
 * The offer, made beside the first destructive confirmation and never again.
 *
 * Both halves of "once" are the vault's own state and neither is a screen's:
 * [VaultState.isOfferable] is the whole question of whether there is anything to offer, and
 * asking it here is what records that the question was put — so a confirmation cannot
 * accidentally offer twice, and a second confirmation cannot accidentally offer at all.
 *
 * Accepting turns the whole vault on (design D1). There is nothing narrower to turn on:
 * the switch governs every trigger, and an offer that armed one of them would be promising
 * something the vault cannot be.
 */
class VaultOfferOnce(
    private val vault: BackupVaultRepository,
) : VaultOffer {

    override fun offerOnce(): VaultOfferTerms? {
        val state = vault.observe().value
        if (!state.isOfferable) return null

        // Recorded now, while the offer is being shown rather than when it is answered:
        // what must not happen twice is the asking.
        vault.markOffered()

        return VaultOfferTerms(
            intervalLabel = VaultInterval.nearest(state.interval).label,
            turnOn = { vault.setOn(true) },
        )
    }
}
