@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.model

import com.neoutils.finance.domain.errors.BuildCreditCardErrors
import com.neoutils.finance.domain.exception.BuildCreditCardException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val errors = BuildCreditCardErrors()

data class CreditCard(
    val id: Long = 0,
    val name: String,
    val limit: Double,
    val closingDay: Int,
    val dueDay: Int,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds()
) {
    init {
        if (name.isBlank()) {
            throw BuildCreditCardException(errors.nameRequired)
        }

        if (limit < 0) {
            throw BuildCreditCardException(errors.limitNegative)
        }

        if (closingDay !in 1..31) {
            throw BuildCreditCardException(errors.closingDayInvalid)
        }

        if (dueDay !in 1..31) {
            throw BuildCreditCardException(errors.dueDayInvalid)
        }
    }
}
