@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import kotlinx.datetime.YearMonth
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
            createRetroactiveInvoiceUseCase(creditCard, targetDueMonth).bind()
        } else {
            createFutureInvoiceUseCase(creditCard, targetDueMonth).bind()
        }
    }
}
