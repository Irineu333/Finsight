@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.exception.CreateFutureInvoiceException
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import kotlin.time.ExperimentalTime
import kotlinx.datetime.plusMonth

class CreateFutureInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository,
    private val creditCardRepository: ICreditCardRepository
) {

    suspend operator fun invoke(creditCardId: Long): Result<Invoice> {
        val creditCard = creditCardRepository.getCreditCardById(creditCardId)
            ?: return Result.failure(CreateFutureInvoiceException("Cartão não encontrado"))

        return invoke(creditCard)
    }

    suspend operator fun invoke(creditCard: CreditCard): Result<Invoice> {
        val existingInvoices = invoiceRepository
            .getInvoicesByCreditCard(creditCard.id)
            .sortedByDescending { it.closingMonth }

        val lastInvoice = existingInvoices.firstOrNull()
            ?: return Result.failure(CreateFutureInvoiceException("Nenhuma fatura existente. Crie uma fatura aberta primeiro."))

        val openingMonth = lastInvoice.closingMonth
        val closingMonth = openingMonth.plusMonth()

        val dueMonth = if (creditCard.dueDay < creditCard.closingDay) {
            closingMonth.plusMonth()
        } else {
            closingMonth
        }

        val overlappingInvoice = existingInvoices.find { existing ->
            openingMonth < existing.closingMonth && closingMonth > existing.openingMonth
        }

        if (overlappingInvoice != null) {
            return Result.failure(CreateFutureInvoiceException("Já existe uma fatura para este período"))
        }

        val invoice = Invoice(
            creditCard = creditCard,
            openingMonth = openingMonth,
            closingMonth = closingMonth,
            dueMonth = dueMonth,
            status = Invoice.Status.FUTURE
        )

        return Result.success(
            invoice.copy(id = invoiceRepository.insert(invoice))
        )
    }
}
