@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.errors.CreateInvoiceErrors
import com.neoutils.finance.domain.exception.CreateInvoiceException
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.extension.toYearMonth
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.plusMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val errors = CreateInvoiceErrors()

class CreateInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository,
    private val creditCardRepository: ICreditCardRepository
) {
    private val currentMonth get() = Clock.System.now().toYearMonth()
    private val nextMonth get() = currentMonth.plusMonth()

    suspend operator fun invoke(creditCardId: Long): Result<Invoice> {

        creditCardRepository.getCreditCardById(creditCardId)
            ?: return Result.failure(CreateInvoiceException(errors.creditCardNotFound))

        val existingInvoices = invoiceRepository.getAllInvoicesByCreditCard(creditCardId)

        val overlappingInvoice = existingInvoices.find { existing ->
            currentMonth < existing.closingMonth && nextMonth > existing.openingMonth
        }

        if (overlappingInvoice != null) {
            return Result.success(overlappingInvoice)
        }

        val newInvoice = Invoice(
            creditCardId = creditCardId,
            openingMonth = currentMonth,
            closingMonth = nextMonth,
            status = Invoice.Status.OPEN,
        )

        return Result.success(
            newInvoice.copy(
                id = invoiceRepository.insert(newInvoice)
            )
        )
    }
}
