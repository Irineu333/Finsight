package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import kotlinx.datetime.LocalDate

class PayCreditCardBillUseCase(
    private val repository: ITransactionRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val calculateCreditCardBillUseCase: CalculateCreditCardBillUseCase
) {
    suspend operator fun invoke(
        invoiceId: Long,
        amount: Double,
        date: LocalDate,
        type: Transaction.Type = Transaction.Type.INVOICE_PAYMENT,
        title: String = "Pagamento de Fatura"
    ) {
        require(amount > 0) { "Payment amount must be positive" }

        val invoice = invoiceRepository.getInvoiceById(invoiceId) ?: return

        // Validar que o valor não excede a fatura
        val transactions = repository.getAllTransactions()
        val currentBillAmount = calculateCreditCardBillUseCase(invoiceId, transactions)
        require(amount <= currentBillAmount) { 
            "Payment amount (R$ %.2f) cannot exceed invoice bill (R$ %.2f)".format(amount, currentBillAmount) 
        }

        val transaction = Transaction(
            type = type,
            amount = -amount,
            title = title,
            date = date,
            category = null,
            target = Transaction.Target.INVOICE_PAYMENT,
            creditCard = creditCardRepository.getCreditCardById(invoice.creditCardId),
            invoice = invoice
        )

        repository.insert(transaction)
    }
}


