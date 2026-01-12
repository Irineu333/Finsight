@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.model.form

import com.neoutils.finance.domain.errors.BuildCreditCardErrors
import com.neoutils.finance.domain.exception.BuildCreditCardException
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.extension.moneyToDouble
import com.neoutils.finance.util.FieldForm
import com.neoutils.finance.util.Validation
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val errors = BuildCreditCardErrors()

data class CreditCardForm(
    val name: FieldForm = FieldForm(),
    val limit: String = "",
    val closingDayUser: String = "",
    val dueDayUser: String = "",
    val closingDayCalc: Int? = null,
    val dueDayCalc: Int? = null,
) {
    val closingDay = closingDayUser.toIntOrNull() ?: closingDayCalc

    val dueDay = dueDayUser.toIntOrNull() ?: dueDayCalc

    fun build(id: Long = 0): Result<CreditCard> {
        if (name.text.isBlank()) {
            return Result.failure(BuildCreditCardException(errors.nameRequired))
        }

        if (name.validation != Validation.Valid) {
            return Result.failure(BuildCreditCardException(errors.nameRequired))
        }

        val limitValue = limit.moneyToDouble()

        if (limitValue < 0) {
            return Result.failure(BuildCreditCardException(errors.limitNegative))
        }

        val closingDay = closingDay ?: return Result.failure(BuildCreditCardException(errors.closingDayRequired))

        if (closingDay !in 1..31) {
            return Result.failure(BuildCreditCardException(errors.closingDayInvalid))
        }

        val dueDay = dueDay ?: return Result.failure(BuildCreditCardException(errors.dueDayRequired))

        if (dueDay !in 1..31) {
            return Result.failure(BuildCreditCardException(errors.dueDayInvalid))
        }

        return Result.success(
            CreditCard(
                id = id,
                name = name.text.trim(),
                limit = limitValue,
                closingDay = closingDay,
                dueDay = dueDay,
                createdAt = Clock.System.now().toEpochMilliseconds()
            )
        )
    }

    fun isValid() = build().isSuccess

    companion object {
        private const val DEFAULT_DAYS_DIFFERENCE = 8

        fun calculateDueDay(closingDay: Int): Int {
            return ((closingDay - 1 + DEFAULT_DAYS_DIFFERENCE) % 31) + 1
        }

        fun calculateClosingDay(dueDay: Int): Int {
            return ((dueDay - 1 - DEFAULT_DAYS_DIFFERENCE + 31) % 31) + 1
        }
    }
}
