@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.left
import com.neoutils.finance.domain.error.InvoiceError
import com.neoutils.finance.domain.error.InvoiceException
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.repository.IInvoiceRepository
import kotlinx.datetime.plusMonth
import kotlin.time.ExperimentalTime

class CreateFutureInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository,
) {
    suspend operator fun invoke(
        creditCard: CreditCard
    ): Either<Throwable, Invoice> {

        val invoices = invoiceRepository
            .getInvoicesByCreditCard(creditCard.id)
            .sortedBy { it.closingMonth }

        val lastInvoice = invoices.lastOrNull()
            ?: return InvoiceException(InvoiceError.NoInvoicesFound).left()

        val openingMonth = lastInvoice.closingMonth
        val closingMonth = openingMonth.plusMonth()

        val dueMonth = if (creditCard.dueDay < creditCard.closingDay) {
            closingMonth.plusMonth()
        } else {
            closingMonth
        }

        val collisions = invoices.filter {
            openingMonth < it.closingMonth && closingMonth > it.openingMonth
        }

        if (collisions.isNotEmpty()) {
            return InvoiceException(InvoiceError.PeriodCollision).left()
        }

        val invoice = Invoice(
            creditCard = creditCard,
            openingMonth = openingMonth,
            closingMonth = closingMonth,
            dueMonth = dueMonth,
            status = Invoice.Status.FUTURE
        )

        return catch {
            invoice.copy(id = invoiceRepository.insert(invoice))
        }
    }
}
