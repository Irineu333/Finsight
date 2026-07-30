package com.neoutils.finsight.database.dao

import androidx.room.Embedded
import com.neoutils.finsight.database.entity.CreditCardEntity

/**
 * A facade row plus what its ledger account states about it: the closure flag and the
 * currency.
 *
 * Closure lives on the account and nowhere else (design D21), but a screen that
 * *renders history* needs both: the facade's name, and whether it still exists as
 * an active choice. The active listings use the filtered queries instead.
 *
 * Category is not here: it owns no account, so it owns its own flag (design D4).
 */
data class CreditCardWithArchival(
    @Embedded val creditCard: CreditCardEntity,
    val isArchived: Boolean,
    /**
     * What the card's `LIABILITY` account is denominated in — read through the same
     * join, never copied onto `credit_cards`. Every figure of a card is in it (design
     * D17), so a card that arrives without it can only be denominated by guessing.
     */
    val currency: String,
)
