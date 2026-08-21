package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.invoiceWindowFor
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import kotlinx.datetime.YearMonth

class CreateInvoiceUseCaseImpl(
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
) : CreateInvoiceUseCase {

    override suspend fun invoke(
        creditCardId: Long,
        dueMonth: YearMonth
    ): Either<Throwable, Invoice> = either {
        val creditCard = ensureNotNull(
            catch { creditCardRepository.getCreditCardById(creditCardId) }.bind()
        ) {
            InvoiceException(InvoiceError.CreditCardNotFound)
        }

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
