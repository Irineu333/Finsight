@file:OptIn(ExperimentalUuidApi::class)

package com.neoutils.finance.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import com.neoutils.finance.domain.error.InstallmentError
import com.neoutils.finance.domain.exception.InstallmentException
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.Installment
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.model.form.TransactionForm
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class AddInstallmentUseCase(
    private val transactionRepository: ITransactionRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val buildTransactionUseCase: BuildTransactionUseCase,
    private val getOrCreateInvoiceForMonthUseCase: GetOrCreateInvoiceForMonthUseCase
) {

    suspend operator fun invoke(
        form: TransactionForm,
        installments: Int,
    ): Either<Throwable, List<Transaction>> {

        if (installments <= 1) {
            return InstallmentException(InstallmentError.MinInstallment).left()
        }

        val creditCard = form.creditCard ?: return InstallmentException(InstallmentError.MissingCreditCard).left()

        val firstInvoice = getOrCreateInvoiceForMonthUseCase(
            creditCard = creditCard,
            targetDueMonth = form.invoiceDueMonth ?: return InstallmentException(InstallmentError.MissingInvoice).left()
        ).fold(
            ifLeft = { return it.left() },
            ifRight = { it }
        )

        val existingInvoices = invoiceRepository
            .getInvoicesByCreditCard(firstInvoice.creditCard.id)
            .sortedBy { it.openingMonth }

        val slots = getSlots(
            firstInvoice = firstInvoice,
            installments = installments,
            invoices = existingInvoices
        )

        return either {
            val slots = validateSlots(slots)
                .mapLeft(::InstallmentException)
                .bind()

            val invoices = getInvoices(slots).bind()

            registerTransactions(form, invoices).bind()
        }
    }

    private fun getSlots(
        firstInvoice: Invoice,
        installments: Int,
        invoices: List<Invoice>
    ): List<InvoiceSlot> {
        val slots = mutableListOf<InvoiceSlot>()
        var dueMonth = firstInvoice.dueMonth

        repeat(installments) { index ->
            val invoice = invoices.find { it.dueMonth == dueMonth }

            slots.add(
                InvoiceSlot(
                    number = index + 1,
                    invoice = invoice,
                    creditCard = firstInvoice.creditCard,
                    dueMonth = dueMonth,
                )
            )

            dueMonth = dueMonth.plus(1, DateTimeUnit.MONTH)
        }

        return slots
    }

    private fun validateSlots(
        slots: List<InvoiceSlot>
    ): Either<InstallmentError, List<InvoiceSlot>> {
        for (slot in slots) {
            val invoice = slot.invoice ?: continue

            if (invoice.status.isBlocked) {
                return InstallmentError.BlockedInvoice(
                    installment = slot.number,
                    invoice = invoice,
                ).left()
            }
        }

        return slots.right()
    }

    private suspend fun getInvoices(
        slots: List<InvoiceSlot>,
    ): Either<Throwable, List<Invoice>> {
        return either {
            slots.map { slot ->
                slot.invoice ?: getOrCreateInvoiceForMonthUseCase(
                    creditCard = slot.creditCard,
                    targetDueMonth = slot.dueMonth
                ).bind()
            }
        }
    }

    private suspend fun registerTransactions(
        form: TransactionForm,
        invoices: List<Invoice>,
    ): Either<Throwable, List<Transaction>> {

        val groupUuid = Uuid.random().toString()

        return either {
            val base = buildTransactionUseCase(form).bind()

            val transactions = invoices.mapIndexed { index, invoice ->
                base.copy(
                    amount = base.amount / invoices.size,
                    date = base.date.plus(index, DateTimeUnit.MONTH),
                    invoice = invoice,
                    installment = Installment(
                        count = invoices.size,
                        number = index + 1,
                        groupUuid = groupUuid,
                        totalAmount = base.amount,
                    ),
                )
            }

            catch {
                transactions.map {
                    it.copy(id = transactionRepository.insert(it))
                }
            }.bind()
        }
    }

    private data class InvoiceSlot(
        val number: Int,
        val creditCard: CreditCard,
        val invoice: Invoice?,
        val dueMonth: YearMonth,
    )
}
