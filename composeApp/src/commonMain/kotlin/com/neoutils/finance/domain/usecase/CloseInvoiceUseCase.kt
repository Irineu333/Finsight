@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.extension.toYearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class CloseInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository,
    private val transactionRepository: ITransactionRepository,
    private val calculateCreditCardBillUseCase: CalculateCreditCardBillUseCase
) {
    suspend operator fun invoke(invoiceId: Long): Result<Unit> {
        val invoice = invoiceRepository.getById(invoiceId)
            ?: return Result.failure(IllegalArgumentException("Invoice not found"))

        if (invoice.status == Invoice.Status.PAID) {
            return Result.failure(IllegalStateException("Cannot close a paid invoice"))
        }

        if (invoice.status == Invoice.Status.CLOSED) {
            return Result.failure(IllegalStateException("Invoice is already closed"))
        }

        val currentMonth = Clock.System.now().toYearMonth()
        if (currentMonth < invoice.closingMonth) {
            return Result.failure(
                IllegalStateException("Cannot close invoice before the closing month")
            )
        }

        // Calcular valor atual da fatura
        val transactions = transactionRepository.getAllTransactions()
        val billAmount = calculateCreditCardBillUseCase(invoiceId, transactions)

        // Não permitir fechamento se valor negativo
        if (billAmount < 0) {
            return Result.failure(
                IllegalStateException("Cannot close invoice with negative balance (R$ %.2f). Please adjust the transactions first.".format(billAmount))
            )
        }

        // Se valor é zero, marcar como PAID diretamente
        if (billAmount == 0.0) {
            invoiceRepository.update(invoice.copy(status = Invoice.Status.PAID))
            return Result.success(Unit)
        }

        // Valor > 0: fechar normalmente (aguardando pagamento)
        invoiceRepository.update(invoice.copy(status = Invoice.Status.CLOSED))
        return Result.success(Unit)
    }
}

