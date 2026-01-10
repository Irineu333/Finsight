@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.errors.RegisterCreditCardErrors
import com.neoutils.finance.domain.exception.RegisterCreditCardException
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.form.CreditCardForm
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.extension.effectiveDay
import com.neoutils.finance.extension.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minusMonth
import kotlinx.datetime.toLocalDateTime

private val errors = RegisterCreditCardErrors()

class AddCreditCardUseCase(
    private val repository: ICreditCardRepository,
    private val openInvoiceUseCase: OpenInvoiceUseCase,
) {
    suspend operator fun invoke(
        form: CreditCardForm
    ): Result<CreditCard> {

        if (form.name.isBlank()) {
            return Result.failure(RegisterCreditCardException(errors.emptyName))
        }

        if (form.limit < 0) {
            return Result.failure(RegisterCreditCardException(errors.negativeLimit))
        }

        if (form.closingDay == null) {
            return Result.failure(
                RegisterCreditCardException(errors.invalidClosingDay)
            )
        }

        if (form.closingDay !in 1..31) {
            return Result.failure(
                RegisterCreditCardException(errors.invalidClosingDay)
            )
        }

        if (form.dueDay == null || form.dueDay !in 1..31) {
            return Result.failure(
                RegisterCreditCardException(errors.invalidDueDay)
            )
        }

        val creditCard = CreditCard(
            name = form.name,
            limit = form.limit,
            closingDay = form.closingDay,
            dueDay = form.dueDay,
            createdAt = Clock.System.now().toEpochMilliseconds()
        ).let {
            it.copy(
                id = repository.insert(it)
            )
        }

        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val effectiveClosingDay = now.yearMonth.effectiveDay(creditCard.closingDay)

        if (now.day < effectiveClosingDay) {
            openInvoiceUseCase(
                creditCardId = creditCard.id,
                openingMonth = now.yearMonth.minusMonth()
            )

            return Result.success(creditCard)
        }

        openInvoiceUseCase(
            creditCardId = creditCard.id,
            openingMonth = now.yearMonth
        )

        return Result.success(creditCard)
    }
}

