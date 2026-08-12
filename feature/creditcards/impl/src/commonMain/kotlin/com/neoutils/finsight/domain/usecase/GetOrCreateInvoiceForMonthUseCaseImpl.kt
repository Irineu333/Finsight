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

/**
 * Finds the invoice a target month already has, or has [CreateInvoiceUseCase] make it.
 *
 * What is its own is the lookup and the refusal of an invoice closed to new spending.
 * Classifying the created invoice is not: that rule has a single owner, in the creation
 * itself, which is what makes an invoice born from a transaction indistinguishable from
 * one born from the user's gesture.
 */
class GetOrCreateInvoiceForMonthUseCaseImpl(
    private val invoiceRepository: IInvoiceRepository,
    private val createInvoiceUseCase: CreateInvoiceUseCase,
) : GetOrCreateInvoiceForMonthUseCase {
    override suspend operator fun invoke(
        creditCard: CreditCard,
        targetDueMonth: YearMonth
    ): Either<Throwable, Invoice> = either {
        val existingInvoice = invoiceRepository
            .getInvoicesByCreditCard(creditCard.id)
            .find { it.dueMonth == targetDueMonth }

        if (existingInvoice != null) {
            ensure(!existingInvoice.status.isClosedToNewExpenses) {
                InvoiceException(
                    InvoiceError.BlockedInvoice(
                        status = existingInvoice.status,
                    )
                )
            }
            return@either existingInvoice
        }

        createInvoiceUseCase(creditCard, targetDueMonth).bind()
    }
}
