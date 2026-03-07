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
    val createdAt: Long = Clock.System.now().toEpochMilliseconds()
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
