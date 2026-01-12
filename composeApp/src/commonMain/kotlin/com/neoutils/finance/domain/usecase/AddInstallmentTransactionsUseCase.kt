package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.extension.safeOnDay
import kotlinx.datetime.plusMonth
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class AddInstallmentTransactionsUseCase(
    private val transactionRepository: ITransactionRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val openInvoiceUseCase: OpenInvoiceUseCase,
    private val createFutureInvoiceUseCase: CreateFutureInvoiceUseCase
) {
    suspend operator fun invoke(
        baseTransaction: Transaction,
        totalInstallments: Int,
        startingInvoice: Invoice
    ): Result<List<Transaction>> {
        if (totalInstallments < 1) {
            return Result.failure(IllegalArgumentException("O número de parcelas deve ser maior que 0"))
        }

        if (totalInstallments == 1) {
            // À vista - transação simples
            val transaction = baseTransaction.copy(
                id = transactionRepository.insert(baseTransaction)
            )
            return Result.success(listOf(transaction))
        }

        val creditCard = baseTransaction.creditCard
            ?: return Result.failure(IllegalArgumentException("Transação parcelada requer cartão de crédito"))

        val installmentAmount = baseTransaction.amount / totalInstallments
        val installmentGroupId = Uuid.random().toString()
        val transactions = mutableListOf<Transaction>()

        var currentInvoice = startingInvoice

        for (installmentNumber in 1..totalInstallments) {
            // Para a primeira parcela, usa a fatura inicial
            // Para as demais, busca ou cria a próxima fatura
            if (installmentNumber > 1) {
                val nextOpeningMonth = currentInvoice.closingMonth

                // Buscar faturas existentes
                val existingInvoices = invoiceRepository.getInvoicesByCreditCard(creditCard.id)
                val nextInvoice = existingInvoices.find { it.openingMonth == nextOpeningMonth }

                currentInvoice = if (nextInvoice != null) {
                    nextInvoice
                } else {
                    // Criar fatura FUTURE
                    createFutureInvoiceUseCase(creditCard).getOrElse { 
                        return Result.failure(it)
                    }
                }
            }

            // Calcular a data da parcela (mesmo dia no mês de abertura da fatura)
            val installmentDate = if (installmentNumber == 1) {
                baseTransaction.date
            } else {
                currentInvoice.openingMonth.safeOnDay(baseTransaction.date.dayOfMonth)
            }

            val installmentTransaction = baseTransaction.copy(
                amount = installmentAmount,
                date = installmentDate,
                invoice = currentInvoice,
                installmentNumber = installmentNumber,
                totalInstallments = totalInstallments,
                installmentGroupId = installmentGroupId
            )

            val insertedId = transactionRepository.insert(installmentTransaction)
            transactions.add(installmentTransaction.copy(id = insertedId))
        }

        return Result.success(transactions)
    }
}
