@file:OptIn(ExperimentalUuidApi::class)

package com.neoutils.finance.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.left
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
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

        return either {

            ensure(installments > 1) {
                InstallmentException(InstallmentError.MinInstallment)
            }

            val creditCard = ensureNotNull(form.creditCard) {
                InstallmentException(InstallmentError.MissingCreditCard)
            }

            val firstInvoice = getOrCreateInvoiceForMonthUseCase(
                creditCard = creditCard,
                targetDueMonth = ensureNotNull(form.invoiceDueMonth) {
                    InstallmentException(InstallmentError.MissingInvoice)
                }
            ).bind()

            val existingInvoices = invoiceRepository
                .getInvoicesByCreditCard(firstInvoice.creditCard.id)
                .sortedBy { it.openingMonth }

            val slots = getSlots(
                firstInvoice = firstInvoice,
                installments = installments,
                invoices = existingInvoices
            ).bind()

            val invoices = getInvoices(slots).bind()

            registerTransactions(form, invoices).bind()
        }
    }

    private fun getSlots(
        firstInvoice: Invoice,
        installments: Int,
        invoices: List<Invoice>
    ): Either<InstallmentException, List<InvoiceSlot>> {
        val slots = mutableListOf<InvoiceSlot>()
        var dueMonth = firstInvoice.dueMonth

        repeat(installments) { index ->
            val invoice = invoices.find { it.dueMonth == dueMonth }

            if (invoice != null && invoice.status.isBlocked) {
                return InstallmentException(
                    InstallmentError.BlockedInvoice(
                        installment = index + 1,
                        invoice = invoice,
                    )
                ).left()
            }

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
