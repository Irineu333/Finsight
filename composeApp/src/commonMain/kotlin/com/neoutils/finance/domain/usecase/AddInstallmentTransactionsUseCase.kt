@file:OptIn(ExperimentalUuidApi::class)

package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.errors.AddInstallmentErrors
import com.neoutils.finance.domain.exception.AddInstallmentException
import com.neoutils.finance.domain.model.Installment
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.extension.then
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val errors = AddInstallmentErrors()

class AddInstallmentTransactionsUseCase(
    private val transactionRepository: ITransactionRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val createFutureInvoiceUseCase: CreateFutureInvoiceUseCase
) {
    suspend operator fun invoke(
        baseTransaction: Transaction,
        totalInstallments: Int,
        startingInvoice: Invoice
    ): Result<List<Transaction>> {

        val creditCard = baseTransaction.creditCard
            ?: return Result.failure(AddInstallmentException(errors.creditCardRequired))

        if (totalInstallments < 1) {
            return Result.failure(AddInstallmentException(errors.invalidInstallmentCount))
        }

        if (totalInstallments == 1) {
            return Result.success(listOf(baseTransaction.copy(id = transactionRepository.insert(baseTransaction))))
        }

        val existingInvoices = invoiceRepository
            .getInvoicesByCreditCard(creditCard.id)
            .sortedBy { it.openingMonth }

        val slots = resolveInvoiceSlots(
            startingInvoice = startingInvoice,
            totalInstallments = totalInstallments,
            existingInvoices = existingInvoices
        )

        return validateSlots(slots)
            .then { slots ->
                registerInvoices(slots, creditCard.id)
            }
            .then { invoices ->
                insertTransactions(baseTransaction, invoices, totalInstallments)
            }
    }

    private fun resolveInvoiceSlots(
        startingInvoice: Invoice,
        totalInstallments: Int,
        existingInvoices: List<Invoice>
    ): List<InvoiceSlot> {
        val slots = mutableListOf<InvoiceSlot>()
        var openingMonth = startingInvoice.openingMonth

        repeat(totalInstallments) { index ->
            val existing = existingInvoices.find { it.openingMonth == openingMonth }

            slots.add(
                InvoiceSlot(
                    number = index + 1,
                    invoice = existing,
                    openingMonth = openingMonth
                )
            )

            openingMonth = existing?.closingMonth ?: openingMonth.plus(1, DateTimeUnit.MONTH)
        }

        return slots
    }

    private fun validateSlots(slots: List<InvoiceSlot>): Result<List<InvoiceSlot>> {
        for (slot in slots) {
            val invoice = slot.invoice ?: continue

            if (invoice.status.isBlocked) {
                return Result.failure(
                    AddInstallmentException(
                        errors.blockedInvoice(slot.number, invoice.status.label.lowercase())
                    )
                )
            }
        }
        return Result.success(slots)
    }

    private suspend fun registerInvoices(
        slots: List<InvoiceSlot>,
        creditCardId: Long
    ): Result<List<Invoice>> {
        val invoices = mutableListOf<Invoice>()

        for (slot in slots) {
            invoices.add(
                slot.invoice ?: createFutureInvoiceUseCase(creditCardId)
                    .getOrElse { return Result.failure(it) }
            )
        }

        return Result.success(invoices)
    }

    private suspend fun insertTransactions(
        baseTransaction: Transaction,
        invoices: List<Invoice>,
        totalInstallments: Int
    ): Result<List<Transaction>> {

        val transactions = invoices.mapIndexed { index, invoice ->
            val transaction = baseTransaction.copy(
                amount = baseTransaction.amount / totalInstallments,
                date = baseTransaction.date.plus(index, DateTimeUnit.MONTH),
                invoice = invoice,
                installment = Installment(
                    count = totalInstallments,
                    number = index + 1,
                    groupUuid = Uuid.random().toString(),
                    totalAmount = baseTransaction.amount,
                ),
            )

            transaction.copy(id = transactionRepository.insert(transaction))
        }

        return Result.success(transactions)
    }

    private data class InvoiceSlot(
        val number: Int,
        val invoice: Invoice?,
        val openingMonth: YearMonth,
    )
}
