@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.extension.toYearMonth
import kotlinx.datetime.plusMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val currentMonth get() = Clock.System.now().toYearMonth()
private val nextMonth get() = currentMonth.plusMonth()

class CreateInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository,
    private val creditCardRepository: ICreditCardRepository
) {

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

        val dueMonth = if (creditCard.dueDay < creditCard.closingDay) {
            closingMonth.plusMonth()
        } else {
            closingMonth
        }

        val newInvoice = Invoice(
            creditCard = creditCard,
            openingMonth = currentMonth,
            closingMonth = closingMonth,
            dueMonth = dueMonth,
            status = Invoice.Status.OPEN,
        )

        newInvoice.copy(
            id = invoiceRepository.insert(newInvoice)
        )
    }
}

