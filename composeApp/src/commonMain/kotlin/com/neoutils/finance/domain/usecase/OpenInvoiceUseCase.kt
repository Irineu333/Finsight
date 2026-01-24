@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.error.OpenInvoiceErrors
import com.neoutils.finance.domain.exception.OpenInvoiceException
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plusMonth
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val errors = OpenInvoiceErrors()

private val currentDate
    get() = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).date

class OpenInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository,
    private val creditCardRepository: ICreditCardRepository
) {
    suspend operator fun invoke(
        creditCardId: Long,
        openingMonth: YearMonth
    ): Result<Invoice> {
        val creditCard = creditCardRepository.getCreditCardById(creditCardId)
            ?: return Result.failure(OpenInvoiceException(errors.creditCardNotFound))

        return invoke(creditCard, openingMonth)
    }

    suspend operator fun invoke(
        creditCard: CreditCard,
        openingMonth: YearMonth
    ): Result<Invoice> {

        val closingMonth = openingMonth.plusMonth()

        val dueMonth = if (creditCard.dueDay < creditCard.closingDay) {
            closingMonth.plusMonth()
        } else {
            closingMonth
        }

        val existingInvoices = invoiceRepository.getInvoicesByCreditCard(creditCard.id)

        val futureInvoice = existingInvoices.find { existing ->
            existing.status == Invoice.Status.FUTURE && existing.openingMonth == openingMonth
        }

        if (futureInvoice != null) {
            return Result.success(
                futureInvoice.copy(
                    status = Invoice.Status.OPEN,
                    openedAt = currentDate
                ).also {
                    invoiceRepository.update(it)
                }
            )
        }

        val overlappingInvoice = existingInvoices.find { existing ->
            openingMonth < existing.closingMonth && closingMonth > existing.openingMonth
        }

        if (overlappingInvoice != null) {
            return Result.failure(
                OpenInvoiceException(errors.overlappingInvoice)
            )
        }

        val invoice = Invoice(
            creditCard = creditCard,
            openingMonth = openingMonth,
            closingMonth = closingMonth,
            dueMonth = dueMonth,
            status = Invoice.Status.OPEN,
            openedAt = currentDate
        )

        return Result.success(
            invoice.copy(id = invoiceRepository.insert(invoice))
        )
    }
}

