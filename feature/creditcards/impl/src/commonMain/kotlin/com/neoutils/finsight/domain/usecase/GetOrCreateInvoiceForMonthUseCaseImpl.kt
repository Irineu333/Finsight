package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import kotlinx.datetime.YearMonth

class GetOrCreateInvoiceForMonthUseCaseImpl(
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val createInvoiceUseCase: CreateInvoiceUseCase,
) : GetOrCreateInvoiceForMonthUseCase {

    override suspend fun invoke(
        creditCardId: Long,
        targetDueMonth: YearMonth
    ): Either<Throwable, Invoice> = either {
        // Resolved before the lookup: an id that matches no card would otherwise find
        // no invoice and go on to create one for a card that does not exist.
        ensureNotNull(
            catch { creditCardRepository.getCreditCardById(creditCardId) }.bind()
        ) {
            InvoiceException(InvoiceError.CreditCardNotFound)
        }

        val existingInvoice = invoiceRepository
            .getInvoicesByCreditCard(creditCardId)
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

        createInvoiceUseCase(creditCardId, targetDueMonth).bind()
    }
}
