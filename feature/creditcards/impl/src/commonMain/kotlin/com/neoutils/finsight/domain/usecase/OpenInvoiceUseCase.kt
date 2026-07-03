@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plusMonth
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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
    ): Either<InvoiceException, Invoice> = either {
        val creditCard = creditCardRepository.getCreditCardById(creditCardId)

        ensureNotNull(creditCard) {
            InvoiceException(InvoiceError.CreditCardNotFound)
        }

        invoke(creditCard, openingMonth).bind()
    }

    suspend operator fun invoke(
        creditCard: CreditCard,
        openingMonth: YearMonth
    ): Either<InvoiceException, Invoice> = either {

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
            return@either futureInvoice.copy(
                status = Invoice.Status.OPEN,
                openedAt = currentDate
            ).also {
                invoiceRepository.update(it)
            }
        }

        val overlappingInvoice = existingInvoices.find { existing ->
            openingMonth < existing.closingMonth && closingMonth > existing.openingMonth
        }

        ensure(overlappingInvoice == null) {
            InvoiceException(InvoiceError.OverlappingInvoice)
        }

        val invoice = Invoice(
            creditCard = creditCard,
            openingMonth = openingMonth,
            closingMonth = closingMonth,
            dueMonth = dueMonth,
            status = Invoice.Status.OPEN,
            openedAt = currentDate
        )

        invoice.copy(id = invoiceRepository.insert(invoice))
    }
}

