@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.error.CreateInvoiceErrors
import com.neoutils.finance.domain.exception.CreateInvoiceException
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.extension.toYearMonth
import kotlinx.datetime.plusMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val errors = CreateInvoiceErrors()

private val currentMonth get() = Clock.System.now().toYearMonth()
private val nextMonth get() = currentMonth.plusMonth()

class CreateInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository,
    private val creditCardRepository: ICreditCardRepository
) {

    suspend operator fun invoke(creditCardId: Long): Result<Invoice> {

        val creditCard = creditCardRepository.getCreditCardById(creditCardId)
            ?: return Result.failure(CreateInvoiceException(errors.creditCardNotFound))

        val existingInvoices = invoiceRepository.getInvoicesByCreditCard(creditCardId)

        val overlappingInvoice = existingInvoices.find { existing ->
            currentMonth < existing.closingMonth && nextMonth > existing.openingMonth
        }

        if (overlappingInvoice != null) {
            return Result.success(overlappingInvoice)
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

        return Result.success(
            newInvoice.copy(
                id = invoiceRepository.insert(newInvoice)
            )
        )
    }
}

