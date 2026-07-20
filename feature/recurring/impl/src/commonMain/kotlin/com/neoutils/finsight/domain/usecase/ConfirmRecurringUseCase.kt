@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.flatMap
import arrow.core.getOrElse
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.OperationIntent
import com.neoutils.finsight.domain.model.OperationLeg
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.extension.monthsUntil
import com.neoutils.finsight.extension.toYearMonth
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class ConfirmRecurringUseCase(
    private val operationRepository: IOperationRepository,
    private val recurringOccurrenceRepository: IRecurringOccurrenceRepository,
    private val getOrCreateInvoiceForMonthUseCase: GetOrCreateInvoiceForMonthUseCase,
) {
    suspend operator fun invoke(
        recurring: Recurring,
        date: LocalDate,
        amount: Double = recurring.amount,
        target: TransactionTarget = if (recurring.creditCard != null) {
            TransactionTarget.CREDIT_CARD
        } else {
            TransactionTarget.ACCOUNT
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
                    OperationIntent(
                        title = recurring.title,
                        date = date,
                        category = recurring.category,
                        recurringId = recurring.id,
                        recurringCycle = cycleNumber,
                        legs = listOf(
                            OperationLeg(
                                type = recurring.type,
                                amount = amount,
                                category = recurring.category,
                                creditCard = targetCreditCard,
                                invoice = invoice,
                            )
                        ),
                    )
                )
            } else {
                val sourceAccount = account ?: recurring.account
                operationRepository.createOperation(
                    OperationIntent(
                        title = recurring.title,
                        date = date,
                        category = recurring.category,
                        recurringId = recurring.id,
                        recurringCycle = cycleNumber,
                        legs = listOf(
                            OperationLeg(
                                type = recurring.type,
                                amount = amount,
                                category = recurring.category,
                                account = sourceAccount,
                            )
                        ),
                    )
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
                        transactionId = operation.id,
                        effectiveDate = date,
                        handledAt = Clock.System.now().toEpochMilliseconds(),
                    )
                )
                operation
            }
        }
    }
}
