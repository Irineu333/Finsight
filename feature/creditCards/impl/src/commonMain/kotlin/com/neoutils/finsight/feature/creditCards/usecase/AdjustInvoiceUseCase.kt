package com.neoutils.finsight.feature.creditCards.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.neoutils.finsight.feature.creditCards.exception.InvoiceNotAdjustedException
import com.neoutils.finsight.feature.creditCards.repository.ICreditCardRepository
import com.neoutils.finsight.core.domain.model.Invoice
import com.neoutils.finsight.core.domain.model.Operation
import com.neoutils.finsight.core.domain.model.Transaction
import com.neoutils.finsight.feature.transactions.repository.IOperationRepository
import com.neoutils.finsight.feature.transactions.repository.ITransactionRepository
import kotlinx.datetime.LocalDate

class AdjustInvoiceUseCase(
    private val repository: ITransactionRepository,
    private val operationRepository: IOperationRepository,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
    private val creditCardRepository: ICreditCardRepository,
) {
    suspend operator fun invoke(
        invoice: Invoice,
        target: Double,
        adjustmentDate: LocalDate
    ): Either<Throwable, Unit> = either {
        val currentInvoice = catch {
            calculateInvoiceUseCase(invoiceId = invoice.id)
        }.bind()

        ensure(target != currentInvoice) { InvoiceNotAdjustedException() }

        val existingAdjustment = catch {
            repository.getTransactionsBy(
                type = Transaction.Type.ADJUSTMENT,
                target = Transaction.Target.CREDIT_CARD,
                invoiceId = invoice.id,
                date = adjustmentDate
            ).firstOrNull()
        }.bind()

        val difference = target - currentInvoice

        catch {
            if (existingAdjustment == null) {
                val creditCard = requireNotNull(
                    creditCardRepository.getCreditCardById(invoice.creditCardId)
                ) { "CreditCard ${invoice.creditCardId} not found" }
                operationRepository.createOperation(
                    kind = Operation.Kind.TRANSACTION,
                    title = null,
                    date = adjustmentDate,
                    categoryId = null,
                    sourceAccountId = null,
                    targetCreditCardId = invoice.creditCardId,
                    targetInvoiceId = invoice.id,
                    transactions = listOf(
                        Transaction(
                            title = null,
                            type = Transaction.Type.ADJUSTMENT,
                            amount = -difference,
                            date = adjustmentDate,
                            target = Transaction.Target.CREDIT_CARD,
                            creditCard = creditCard,
                            invoice = invoice
                        )
                    ),
                )
                return@catch
            }

            val newAmount = existingAdjustment.amount - difference

            if (newAmount == 0.0) {
                val operationId = existingAdjustment.operationId
                if (operationId != null) {
                    operationRepository.deleteOperationById(operationId)
                } else {
                    repository.delete(existingAdjustment)
                }
                return@catch
            }

            repository.update(
                existingAdjustment.copy(amount = newAmount)
            )
        }.bind()
    }
}