@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.feature.recurring.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.flatMap
import arrow.core.getOrElse
import com.neoutils.finsight.core.domain.model.Account
import com.neoutils.finsight.core.domain.model.CreditCard
import com.neoutils.finsight.core.domain.model.Invoice
import com.neoutils.finsight.feature.transactions.model.Operation
import com.neoutils.finsight.feature.recurring.model.Recurring
import com.neoutils.finsight.feature.recurring.model.RecurringOccurrence
import com.neoutils.finsight.feature.transactions.model.Transaction
import com.neoutils.finsight.feature.transactions.repository.IOperationRepository
import com.neoutils.finsight.feature.recurring.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.feature.creditCards.usecase.IGetOrCreateInvoiceForMonthUseCase
import com.neoutils.finsight.core.utils.extension.monthsUntil
import com.neoutils.finsight.core.utils.extension.toYearMonth
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class ConfirmRecurringUseCase(
    private val operationRepository: IOperationRepository,
    private val recurringOccurrenceRepository: IRecurringOccurrenceRepository,
    private val getOrCreateInvoiceForMonthUseCase: IGetOrCreateInvoiceForMonthUseCase,
) {
    suspend operator fun invoke(
        recurring: Recurring,
        date: LocalDate,
        amount: Double = recurring.amount,
        target: Transaction.Target = if (recurring.creditCard != null) {
            Transaction.Target.CREDIT_CARD
        } else {
            Transaction.Target.ACCOUNT
        },
        account: Account? = recurring.account,
        creditCard: CreditCard? = recurring.creditCard,
        invoice: Invoice? = null,
    ): Either<Throwable, Operation> {
        val yearMonth = date.yearMonth
        val cycleNumber = Instant
            .fromEpochMilliseconds(recurring.createdAt)
            .toYearMonth()
            .monthsUntil(yearMonth) + 1

        return catch {
            val existingOccurrence = recurringOccurrenceRepository.getOccurrenceBy(recurring.id, yearMonth)
            require(existingOccurrence?.status != RecurringOccurrence.Status.CONFIRMED) {
                "Recurring already confirmed for $yearMonth"
            }

            if (target.isCreditCard) {
                val targetCreditCard = creditCard ?: recurring.creditCard
                requireNotNull(targetCreditCard) { "Credit card is required for recurring confirmation" }

                val invoice = invoice
                    ?: getOrCreateInvoiceForMonthUseCase(targetCreditCard, yearMonth)
                        .getOrElse { throw it }

                operationRepository.createOperation(
                    kind = Operation.Kind.TRANSACTION,
                    title = recurring.title,
                    date = date,
                    categoryId = recurring.category?.id,
                    sourceAccountId = null,
                    targetCreditCardId = targetCreditCard.id,
                    targetInvoiceId = invoice.id,
                    recurringId = recurring.id,
                    recurringCycle = cycleNumber,
                    transactions = listOf(
                        Transaction(
                            type = when (recurring.type) {
                                Recurring.Type.INCOME -> Transaction.Type.INCOME
                                Recurring.Type.EXPENSE -> Transaction.Type.EXPENSE
                            },
                            amount = amount,
                            title = recurring.title,
                            date = date,
                            category = recurring.category,
                            target = Transaction.Target.CREDIT_CARD,
                            creditCard = targetCreditCard,
                            invoice = invoice,
                        )
                    ),
                )
            } else {
                val sourceAccount = account ?: recurring.account
                operationRepository.createOperation(
                    kind = Operation.Kind.TRANSACTION,
                    title = recurring.title,
                    date = date,
                    categoryId = recurring.category?.id,
                    sourceAccountId = sourceAccount?.id,
                    targetCreditCardId = null,
                    targetInvoiceId = null,
                    recurringId = recurring.id,
                    recurringCycle = cycleNumber,
                    transactions = listOf(
                        Transaction(
                            type = when (recurring.type) {
                                Recurring.Type.INCOME -> Transaction.Type.INCOME
                                Recurring.Type.EXPENSE -> Transaction.Type.EXPENSE
                            },
                            amount = amount,
                            title = recurring.title,
                            date = date,
                            category = recurring.category,
                            target = Transaction.Target.ACCOUNT,
                            account = sourceAccount,
                        )
                    ),
                )
            }
        }.flatMap { operation ->
            catch {
                recurringOccurrenceRepository.save(
                    RecurringOccurrence(
                        recurringId = recurring.id,
                        cycleNumber = cycleNumber,
                        yearMonth = yearMonth,
                        status = RecurringOccurrence.Status.CONFIRMED,
                        operationId = operation.id,
                        effectiveDate = date,
                        handledAt = Clock.System.now().toEpochMilliseconds(),
                    )
                )
                operation
            }
        }
    }
}
