@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.model

import com.neoutils.finance.domain.errors.RegisterCreditCardErrors
import com.neoutils.finance.domain.exception.RegisterCreditCardException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val creditCardErrors = RegisterCreditCardErrors()

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
            throw RegisterCreditCardException(creditCardErrors.emptyName)
        }

        if (limit < 0) {
            throw RegisterCreditCardException(creditCardErrors.negativeLimit)
        }

        if (closingDay !in 1..28) {
            throw RegisterCreditCardException(creditCardErrors.invalidClosingDay)
        }

        if (dueDay !in 1..28) {
            throw RegisterCreditCardException(creditCardErrors.invalidDueDay)
        }
    }
}