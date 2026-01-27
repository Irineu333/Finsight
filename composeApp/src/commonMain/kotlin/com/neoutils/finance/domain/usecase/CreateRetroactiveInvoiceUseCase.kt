@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.neoutils.finance.domain.error.InvoiceError
import com.neoutils.finance.domain.error.InvoiceException
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.repository.IInvoiceRepository
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minusMonth
import kotlin.time.ExperimentalTime

class CreateRetroactiveInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository
) {
    suspend operator fun invoke(
        creditCard: CreditCard,
        targetDueMonth: YearMonth
    ): Either<Throwable, Invoice> = either {
        val collisions = invoiceRepository
            .getInvoicesByCreditCard(creditCard.id)
            .find { it.dueMonth == targetDueMonth }

        ensure(collisions == null) {
            InvoiceException(InvoiceError.AlreadyExists)
        }

        val closingMonth = if (creditCard.dueDay < creditCard.closingDay) {
            targetDueMonth.minusMonth()
        } else {
            targetDueMonth
        }

        val openingMonth = closingMonth.minusMonth()

        val invoice = Invoice(
            creditCard = creditCard,
            openingMonth = openingMonth,
            closingMonth = closingMonth,
            dueMonth = targetDueMonth,
            status = Invoice.Status.RETROACTIVE
        )

        catch {
            invoice.copy(id = invoiceRepository.insert(invoice))
        }.bind()
    }
}
