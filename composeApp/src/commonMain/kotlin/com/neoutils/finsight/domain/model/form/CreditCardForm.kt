@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.model.form

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.neoutils.finsight.domain.error.CreditCardError
import com.neoutils.finsight.domain.exception.CreditCardException
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.extension.moneyToDouble
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class CreditCardForm(
    val name: String = "",
    val limit: String = "",
    val closingDayUser: String = "",
    val dueDayUser: String = "",
    val closingDayCalc: Int? = null,
    val dueDayCalc: Int? = null,
) {
    val closingDay = closingDayUser.toIntOrNull() ?: closingDayCalc

    val dueDay = dueDayUser.toIntOrNull() ?: dueDayCalc

    fun build(id: Long = 0): Either<CreditCardException, CreditCard> {
        if (name.isBlank()) {
            return CreditCardException(CreditCardError.EMPTY_NAME).left()
        }

        val limitValue = limit.moneyToDouble()

        if (limitValue < 0) {
            return CreditCardException(CreditCardError.NEGATIVE_LIMIT).left()
        }

        val closingDay = closingDay ?: return CreditCardException(CreditCardError.MISSING_CLOSING_DAY).left()

        if (closingDay !in 1..31) {
            return CreditCardException(CreditCardError.INVALID_CLOSING_DAY).left()
        }

        val dueDay = dueDay ?: return CreditCardException(CreditCardError.MISSING_DUE_DAY).left()

        if (dueDay !in 1..31) {
            return CreditCardException(CreditCardError.INVALID_DUE_DAY).left()
        }

        return CreditCard(
            id = id,
            name = name.trim(),
            limit = limitValue,
            closingDay = closingDay,
            dueDay = dueDay,
            createdAt = Clock.System.now().toEpochMilliseconds()
        ).right()
    }

    fun isValid() = build().isRight()
    fun isInvalid() = build().isLeft()
}
