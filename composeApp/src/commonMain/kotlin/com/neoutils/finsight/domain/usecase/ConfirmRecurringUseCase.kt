@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.flatMap
import arrow.core.getOrElse
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.extension.toYearMonth
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ConfirmRecurringUseCase(
    private val operationRepository: IOperationRepository,
    private val recurringRepository: IRecurringRepository,
    private val getOrCreateInvoiceForMonthUseCase: GetOrCreateInvoiceForMonthUseCase,
) {
    suspend operator fun invoke(
        recurring: Recurring,
        date: LocalDate,
        amount: Double = recurring.amount,
        invoice: Invoice? = null,
    ): Either<Throwable, Operation> {
        val creditCard = recurring.creditCard

        return catch {
            if (creditCard != null) {
                val invoice = invoice
                    ?: getOrCreateInvoiceForMonthUseCase(creditCard, date.yearMonth)
                        .getOrElse { throw it }

                operationRepository.createOperation(
                    kind = Operation.Kind.TRANSACTION,
                    title = recurring.title,
                    date = date,
                    categoryId = recurring.category?.id,
                    sourceAccountId = null,
                    targetCreditCardId = creditCard.id,
                    targetInvoiceId = invoice.id,
                    transactions = listOf(
                        Transaction(
                            type = recurring.type,
                            amount = amount,
                            title = recurring.title,
                            date = date,
                            category = recurring.category,
                            target = Transaction.Target.CREDIT_CARD,
                            creditCard = creditCard,
                            invoice = invoice,
                        )
                    ),
                )
            } else {
                operationRepository.createOperation(
                    kind = Operation.Kind.TRANSACTION,
                    title = recurring.title,
                    date = date,
                    categoryId = recurring.category?.id,
                    sourceAccountId = recurring.account?.id,
                    targetCreditCardId = null,
                    targetInvoiceId = null,
                    transactions = listOf(
                        Transaction(
                            type = recurring.type,
                            amount = amount,
                            title = recurring.title,
                            date = date,
                            category = recurring.category,
                            target = Transaction.Target.ACCOUNT,
                            account = recurring.account,
                        )
                    ),
                )
            }
        }.flatMap { operation ->
            catch {
                recurringRepository.update(
                    recurring.copy(lastHandledYearMonth = Clock.System.now().toYearMonth())
                )
                operation
            }
        }
    }
}
