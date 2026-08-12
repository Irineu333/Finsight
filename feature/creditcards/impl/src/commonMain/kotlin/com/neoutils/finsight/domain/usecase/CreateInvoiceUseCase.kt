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
import kotlinx.datetime.YearMonth

/**
 * Brings an invoice into existence for a target due month.
 *
 * The single way an invoice is born from a month: the user's explicit gesture and the
 * on-demand creation of a transaction both come through here, so the two cannot produce
 * invoices that differ. What is declared is the *cycle*, never its value — the window and
 * the due month derive from the card, and what the invoice is worth comes later, from
 * entries or from a balance adjustment.
 *
 * The status is derived, never chosen: a month falling due before the open invoice's due
 * month is [Invoice.Status.RETROACTIVE], from it onwards [Invoice.Status.FUTURE]. The
 * reference is the open invoice and not today, so a card whose open invoice fell behind
 * keeps the same criterion without a special case. Opening remains exclusive to
 * `OpenInvoiceUseCase`: this operation never produces [Invoice.Status.OPEN].
 */
class CreateInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository,
) {
    suspend operator fun invoke(
        creditCard: CreditCard,
        dueMonth: YearMonth
    ): Either<Throwable, Invoice> = either {
        val invoices = invoiceRepository.getInvoicesByCreditCard(creditCard.id)

        ensure(invoices.none { it.dueMonth == dueMonth }) {
            InvoiceException(InvoiceError.AlreadyExists)
        }

        val openInvoice = invoices.find { it.status.isOpen }
            ?: raise(InvoiceException(InvoiceError.NoOpenInvoice))

        val window = creditCard.invoiceWindowFor(dueMonth)

        val invoice = Invoice(
            creditCard = creditCard,
            openingMonth = window.openingMonth,
            closingMonth = window.closingMonth,
            dueMonth = dueMonth,
            status = if (dueMonth < openInvoice.dueMonth) {
                Invoice.Status.RETROACTIVE
            } else {
                Invoice.Status.FUTURE
            }
        )

        catch {
            invoiceRepository.insert(invoice)
        }.bind()
    }
}
