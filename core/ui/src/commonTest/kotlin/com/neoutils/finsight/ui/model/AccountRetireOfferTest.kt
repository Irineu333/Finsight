package com.neoutils.finsight.ui.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The account-only wrapper around [retireActionOf]: the default account offers no
 * retire at all (a third case), every other account delegates the archive-vs-delete
 * decision to the shared, untouched rule.
 */
class AccountRetireOfferTest {

    @Test
    fun `the default account cannot be retired but still names the action for a disabled button`() {
        assertEquals(
            AccountRetireOffer.UnavailableDefault(RetireAction.DELETE),
            accountRetireOfferOf(hasMovement = false, isDefault = true),
        )
        assertEquals(
            AccountRetireOffer.UnavailableDefault(RetireAction.ARCHIVE),
            accountRetireOfferOf(hasMovement = true, isDefault = true),
        )
    }

    @Test
    fun `a non-default account delegates to the shared archive-vs-delete rule`() {
        assertEquals(
            AccountRetireOffer.Retire(RetireAction.ARCHIVE),
            accountRetireOfferOf(hasMovement = true, isDefault = false),
        )
        assertEquals(
            AccountRetireOffer.Retire(RetireAction.DELETE),
            accountRetireOfferOf(hasMovement = false, isDefault = false),
        )
    }
}
