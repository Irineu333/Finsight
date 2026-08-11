@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.dueMonthFor
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.extension.currentYearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.plusMonth

class CreateInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val clock: Clock,
) {

    private val currentMonth get() = clock.currentYearMonth()
    private val nextMonth get() = currentMonth.plusMonth()


    suspend operator fun invoke(
        creditCardId: Long
    ): Either<InvoiceException, Invoice> = either {
        val creditCard = creditCardRepository.getCreditCardById(creditCardId)

        ensureNotNull(creditCard) {
            InvoiceException(InvoiceError.CreditCardNotFound)
        }

        val existingInvoices = invoiceRepository.getInvoicesByCreditCard(creditCardId)

        val overlappingInvoice = existingInvoices.find { existing ->
            currentMonth < existing.closingMonth && nextMonth > existing.openingMonth
        }

        if (overlappingInvoice != null) {
            return@either overlappingInvoice
        }

        val closingMonth = nextMonth

        val dueMonth = creditCard.dueMonthFor(closingMonth)

        val newInvoice = Invoice(
            creditCard = creditCard,
            openingMonth = currentMonth,
            closingMonth = closingMonth,
            dueMonth = dueMonth,
            status = Invoice.Status.OPEN,
        )

        invoiceRepository.insert(newInvoice)
    }
}

