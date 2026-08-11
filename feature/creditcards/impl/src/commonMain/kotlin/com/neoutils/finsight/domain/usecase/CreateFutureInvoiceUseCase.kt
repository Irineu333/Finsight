@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.invoiceWindowFor
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import kotlin.time.ExperimentalTime
import kotlinx.datetime.YearMonth

class CreateFutureInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository,
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

        val window = creditCard.invoiceWindowFor(targetDueMonth)

        val invoice = Invoice(
            creditCard = creditCard,
            openingMonth = window.openingMonth,
            closingMonth = window.closingMonth,
            dueMonth = targetDueMonth,
            status = Invoice.Status.FUTURE
        )

        catch {
            invoiceRepository.insert(invoice)
        }.bind()
    }
}
