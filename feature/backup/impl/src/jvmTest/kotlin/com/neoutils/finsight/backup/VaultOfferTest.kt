package com.neoutils.finsight.backup

import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.domain.vault.VaultOfferOnce
import com.neoutils.finsight.domain.vault.VaultInterval
import com.neoutils.finsight.domain.vault.label
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/**
 * The offer beside a destructive confirmation: what it costs to accept, and the fact that
 * it is put once.
 *
 * The asserting is against the vault rather than against the return value, because the
 * requirement is what accepting *does*: an offer that said yes and left the vault off, or
 * one that armed a single trigger, would satisfy "it returned terms" and protect nobody.
 */
class VaultOfferTest {

    private val vault = BackupVaultRepository(MapSettings())
    private val offer = VaultOfferOnce(vault)

    @Test
    fun `one yes turns the whole vault on`() {
        val terms = assertNotNull(offer.offerOnce(), "a vault that is off is offered")

        assertFalse(vault.observe().value.isOn, "showing the offer decides nothing")

        terms.accept()

        val state = vault.observe().value
        assertTrue(state.isOn, "accepting turns the vault on, not one copy")
        assertTrue(state.isPeriodicOn, "and every trigger with it (design D1)")
        assertTrue(state.isPreventiveOn)
    }

    /**
     * Somebody who said no is not asked again every time they destroy something. What must
     * not happen twice is the asking, so it is the showing that is recorded and not the
     * answer.
     */
    @Test
    fun `the offer is made once, whatever the answer was`() {
        assertNotNull(offer.offerOnce(), "the first time")

        assertNull(offer.offerOnce(), "and not again, though nobody ever accepted")
        assertFalse(vault.observe().value.isOn)
    }

    /**
     * Five confirmations across three features carry the offer, and the person meets
     * whichever they reach first. The gate is the vault's own state, so the second, third
     * and fifth sheet find nothing to show however many of them are built — one gate, and
     * never one per confirmation.
     */
    @Test
    fun `the offer rides on whichever of the five comes first, and on none of the rest`() {
        val confirmations = List(5) { VaultOfferOnce(vault) }

        val offered = confirmations.mapNotNull { it.offerOnce() }

        assertEquals(1, offered.size, "the offer was made more than once, or not at all")
        assertTrue(vault.observe().value.wasOffered, "and the asking is what is recorded")
    }

    /**
     * A confirmation reached after the vault is on has nothing to offer either, and that is
     * the same question rather than a second one: there is nothing left to turn on.
     */
    @Test
    fun `accepting on the first stops every later confirmation from offering`() {
        assertNotNull(offer.offerOnce()).accept()

        assertNull(VaultOfferOnce(vault).offerOnce())
    }

    @Test
    fun `a vault that is already on has nothing to offer`() {
        vault.setOn(true)

        assertNull(offer.offerOnce())
    }

    /**
     * The sentence beside the box names the wait in force, because accepting buys that
     * wait from now on and not this one copy.
     */
    @Test
    fun `the terms state the interval in force`() {
        vault.setInterval(7.days)

        assertEquals(
            VaultInterval.SEVEN_DAYS.label,
            assertNotNull(offer.offerOnce()).intervalLabel,
        )
    }
}
