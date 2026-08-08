package com.neoutils.finsight.ui.modal.confirmRecurring

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A confirmation is only ever offered destinations the domain would accept (design D26).
 *
 * The rule reads the same for accounts and for cards, which is the point: it had two
 * copies and the card's copy was missing, so a BRL recurring could be pointed at a dollar
 * card, the field would redress itself in `US$`, Confirm would stay enabled, and the
 * refusal would arrive as a generic error modal — the path D26 exists to make unreachable.
 */
class OfferedForCurrencyTest {

    private data class Destination(val name: String, val currency: String?)

    private val destinations = listOf(
        Destination("Nubank", "BRL"),
        Destination("Chase", "USD"),
        Destination("Amex", "USD"),
    )

    @Test
    fun `only destinations in the recurring's currency are offered`() {
        val offered = destinations.offeredFor("USD") { it.currency }

        assertEquals(listOf("Chase", "Amex"), offered.map { it.name })
    }

    @Test
    fun `a recurring that names neither account nor card constrains nothing`() {
        val offered = destinations.offeredFor(null) { it.currency }

        assertEquals(destinations, offered, "there is no denomination to respect, so nothing is hidden")
    }

    /**
     * The single-currency case, and the one that matters most: the list is untouched, so
     * the note explaining a shorter list never appears for a user who holds one currency.
     */
    @Test
    fun `a single-currency user sees every destination and no filtering`() {
        val brlOnly = listOf(Destination("Nubank", "BRL"), Destination("Itaú", "BRL"))

        val offered = brlOnly.offeredFor("BRL") { it.currency }

        assertEquals(brlOnly, offered)
        assertEquals(brlOnly.size, offered.size, "nothing was hidden, so nothing is explained away")
    }

    /**
     * A destination whose currency is unknown is not offered. It is the card read through a
     * path that did not join its `LIABILITY` account, and guessing it belongs would be
     * guessing the very fact the filter exists to check.
     */
    @Test
    fun `a destination with no currency is not offered`() {
        val offered = (destinations + Destination("Unhydrated", null)).offeredFor("BRL") { it.currency }

        assertEquals(listOf("Nubank"), offered.map { it.name })
    }
}
