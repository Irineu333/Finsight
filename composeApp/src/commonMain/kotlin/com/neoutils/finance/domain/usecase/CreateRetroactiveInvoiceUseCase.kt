@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.exception.CreateRetroactiveInvoiceException
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
    ): Result<Invoice> {
        val existingInvoice = invoiceRepository
            .getInvoicesByCreditCard(creditCard.id)
            .find { it.dueMonth == targetDueMonth }

        if (existingInvoice != null) {
            return Result.failure(
                CreateRetroactiveInvoiceException("Já existe uma fatura para este mês")
            )
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

        return Result.success(
            invoice.copy(id = invoiceRepository.insert(invoice))
        )
    }
}
