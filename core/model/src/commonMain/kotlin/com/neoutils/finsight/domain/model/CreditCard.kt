@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.model

import com.neoutils.finsight.domain.error.CreditCardError
import com.neoutils.finsight.domain.exception.CreditCardException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class CreditCard(
    val id: Long = 0,
    val name: String,
    val limit: Double,
    val closingDay: Int,
    val dueDay: Int,
    val iconKey: String = "card",
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    // The chart-of-accounts LIABILITY row this card projects onto.
    // Assigned by the store on insert, exactly like [id].
    val accountId: Long = 0,
    // Mirrors the closure of its ledger account (D21).
    val isArchived: Boolean = false,
    // Mirrors the currency of its ledger account, exactly like [isArchived]: the card is a
    // facade over a LIABILITY row, and that row is where a currency is decided and stored.
    // Carried rather than looked up because every invoice figure is denominated by its card,
    // and a mapper handed the card should not have to reach for the chart of accounts to
    // learn what the number in front of it means. No default, like the account's: the card
    // form is the door a currency comes in through, and it is the only site that chooses one.
    val currency: String,
) {
    init {
        if (name.isBlank()) {
            throw CreditCardException(CreditCardError.EMPTY_NAME)
        }

        if (limit < 0) {
            throw CreditCardException(CreditCardError.NEGATIVE_LIMIT)
        }

        if (closingDay !in 1..31) {
            throw CreditCardException(CreditCardError.INVALID_CLOSING_DAY)
        }

        if (dueDay !in 1..31) {
            throw CreditCardException(CreditCardError.INVALID_DUE_DAY)
        }
    }
}
