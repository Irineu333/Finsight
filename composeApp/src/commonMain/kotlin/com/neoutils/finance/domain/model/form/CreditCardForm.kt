@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.model.form

import com.neoutils.finance.domain.errors.BuildCreditCardErrors
import com.neoutils.finance.domain.exception.CreditCardException
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.extension.moneyToDouble
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val errors = BuildCreditCardErrors()

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

    fun build(id: Long = 0): Result<CreditCard> {
        if (name.isBlank()) {
            return Result.failure(CreditCardException(errors.nameRequired))
        }

        val limitValue = limit.moneyToDouble()

        if (limitValue < 0) {
            return Result.failure(CreditCardException(errors.limitNegative))
        }

        val closingDay = closingDay ?: return Result.failure(CreditCardException(errors.closingDayRequired))

        if (closingDay !in 1..31) {
            return Result.failure(CreditCardException(errors.closingDayInvalid))
        }

        val dueDay = dueDay ?: return Result.failure(CreditCardException(errors.dueDayRequired))

        if (dueDay !in 1..31) {
            return Result.failure(CreditCardException(errors.dueDayInvalid))
        }

        return Result.success(
            CreditCard(
                id = id,
                name = name.trim(),
                limit = limitValue,
                closingDay = closingDay,
                dueDay = dueDay,
                createdAt = Clock.System.now().toEpochMilliseconds()
            )
        )
    }

    fun isValid() = build().isSuccess
}
