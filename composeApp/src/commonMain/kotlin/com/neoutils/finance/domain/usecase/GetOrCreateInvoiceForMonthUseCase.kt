@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.neoutils.finance.domain.error.InvoiceError
import com.neoutils.finance.domain.error.InvoiceException
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.repository.IInvoiceRepository
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plusMonth
import kotlin.time.ExperimentalTime

class GetOrCreateInvoiceForMonthUseCase(
    private val invoiceRepository: IInvoiceRepository,
    private val createFutureInvoiceUseCase: CreateFutureInvoiceUseCase,
    private val createRetroactiveInvoiceUseCase: CreateRetroactiveInvoiceUseCase
) {
    suspend operator fun invoke(
        creditCard: CreditCard,
        targetDueMonth: YearMonth
    ): Either<Throwable, Invoice> = either {
        val invoices = invoiceRepository
            .getInvoicesByCreditCard(creditCard.id)
            .sortedBy { it.closingMonth }

        val existingInvoice = invoices.find { it.dueMonth == targetDueMonth }

        if (existingInvoice != null) {
            ensure(!existingInvoice.status.isBlocked) {
                InvoiceException(
                    InvoiceError.BlockedInvoice(
                        status = existingInvoice.status,
                    )
                )
            }
            return@either existingInvoice
        }

        val openInvoice = invoices.find { it.status.isOpen }
            ?: raise(InvoiceException(InvoiceError.NoOpenInvoice))

        if (targetDueMonth < openInvoice.dueMonth) {
            return@either createRetroactiveInvoiceUseCase(creditCard, targetDueMonth).bind()
        }

        val latestInvoice = invoices.lastOrNull()
            ?: raise(InvoiceException(InvoiceError.NoInvoicesFound))

        createInvoicesUntilTarget(
            creditCard = creditCard,
            targetDueMonth = targetDueMonth,
            existingInvoices = invoices,
            currentInvoice = latestInvoice
        ).bind()
    }

    private suspend fun createInvoicesUntilTarget(
        creditCard: CreditCard,
        targetDueMonth: YearMonth,
        existingInvoices: List<Invoice>,
        currentInvoice: Invoice
    ): Either<Throwable, Invoice> = either {
        var current = currentInvoice

        while (current.dueMonth < targetDueMonth) {
            val nextDueMonth = calculateNextDueMonth(current, creditCard)
            current = existingInvoices.find { it.dueMonth == nextDueMonth }
                ?: createFutureInvoiceUseCase(creditCard).bind()
        }

        current
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
