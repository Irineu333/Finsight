@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.exception.CreateFutureInvoiceException
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.repository.IInvoiceRepository
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plusMonth
import kotlin.time.ExperimentalTime

class GetOrCreateInvoiceForMonthUseCase(
    private val invoiceRepository: IInvoiceRepository,
    private val createFutureInvoiceUseCase: CreateFutureInvoiceUseCase
) {

    suspend operator fun invoke(
        creditCard: CreditCard,
        targetDueMonth: YearMonth
    ): Result<Invoice> {
        val existingInvoices = invoiceRepository
            .getInvoicesByCreditCard(creditCard.id)
            .sortedByDescending { it.closingMonth }

        val existingInvoice = existingInvoices.find { it.dueMonth == targetDueMonth }
        if (existingInvoice != null) {
            return Result.success(existingInvoice)
        }

        val openInvoice = existingInvoices.find { it.status.isOpen }
            ?: return Result.failure(CreateFutureInvoiceException("Nenhuma fatura aberta encontrada"))

        if (targetDueMonth < openInvoice.dueMonth) {
            return Result.failure(CreateFutureInvoiceException("Não é possível criar fatura anterior à fatura aberta"))
        }

        var currentInvoice = existingInvoices.first()

        while (currentInvoice.dueMonth < targetDueMonth) {
            val nextDueMonth = calculateNextDueMonth(currentInvoice, creditCard)

            val nextExisting = existingInvoices.find { it.dueMonth == nextDueMonth }
            if (nextExisting != null) {
                currentInvoice = nextExisting
                continue
            }

            val newInvoice = createFutureInvoiceUseCase(creditCard).getOrElse {
                return Result.failure(it)
            }
            currentInvoice = newInvoice

            if (currentInvoice.dueMonth == targetDueMonth) {
                return Result.success(currentInvoice)
            }
        }

        return Result.success(currentInvoice)
    }

    private fun calculateNextDueMonth(invoice: Invoice, creditCard: CreditCard): YearMonth {
        val nextClosingMonth = invoice.closingMonth.plusMonth()
        return if (creditCard.dueDay < creditCard.closingDay) {
            nextClosingMonth.plusMonth()
        } else {
            nextClosingMonth
        }
    }
}
