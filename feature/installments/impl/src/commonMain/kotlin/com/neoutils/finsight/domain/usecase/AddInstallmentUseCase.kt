package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.left
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import arrow.core.right
import com.neoutils.finsight.domain.error.InstallmentError
import com.neoutils.finsight.domain.exception.InstallmentException
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.IOperationRepository
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus

class AddInstallmentUseCase(
    private val operationRepository: IOperationRepository,
    private val installmentRepository: IInstallmentRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val buildTransactionUseCase: IBuildTransactionUseCase,
    private val getOrCreateInvoiceForMonthUseCase: IGetOrCreateInvoiceForMonthUseCase,
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
        return either {
            val base = buildTransactionUseCase(form).bind()

            val installmentId = installmentRepository.createInstallment(
                count = invoices.size,
                totalAmount = base.amount,
            )

            val transactions = invoices.mapIndexed { index, invoice ->
                base.copy(
                    amount = base.amount / invoices.size,
                    date = base.date.plus(index, DateTimeUnit.MONTH),
                    invoice = invoice,
                )
            }

            catch {
                transactions.mapIndexed { index, transaction ->
                    operationRepository.createOperation(
                        kind = Operation.Kind.TRANSACTION,
                        title = transaction.title,
                        date = transaction.date,
                        categoryId = transaction.category?.id,
                        sourceAccountId = transaction.account?.id,
                        targetCreditCardId = transaction.creditCard?.id,
                        targetInvoiceId = transaction.invoice?.id,
                        installmentId = installmentId,
                        installmentNumber = index + 1,
                        transactions = listOf(transaction),
                    ).primaryTransaction
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
