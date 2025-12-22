@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.repository.ICreditCardRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

class AddCreditCardUseCase(
        private val repository: ICreditCardRepository,
        private val openInvoiceUseCase: OpenInvoiceUseCase
) {
    suspend operator fun invoke(name: String, limit: Double, closingDay: Int? = null): Long {
        require(name.isNotBlank()) { "Credit card name cannot be blank" }
        require(limit >= 0) { "Credit card limit must be non-negative" }
        require(closingDay == null || closingDay in 1..28) {
            "Closing day must be between 1 and 28"
        }

        val creditCard =
                CreditCard(
                        name = name,
                        limit = limit,
                        closingDay = closingDay,
                        createdAt = Clock.System.now().toEpochMilliseconds()
                )

        val creditCardId = repository.insert(creditCard)

        if (closingDay != null) {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val today = now.date.day

            val openingMonth =
                    if (today < closingDay) {
                        YearMonth(now.year, now.month).minus(1, DateTimeUnit.MONTH)
                    } else {
                        YearMonth(now.year, now.month)
                    }

            openInvoiceUseCase(creditCardId, openingMonth)
        }

        return creditCardId
    }
}
