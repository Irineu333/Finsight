@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.flatMap
import arrow.core.getOrElse
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.extension.monthsUntil
import com.neoutils.finsight.extension.toYearMonth
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class ConfirmRecurringUseCase(
    private val transactionRepository: ITransactionRepository,
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
    ): Either<Throwable, Transaction> {
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

                transactionRepository.createTransaction(
                    TransactionIntent(
                        title = recurring.title,
                        date = date,
                        category = recurring.category,
                        recurringId = recurring.id,
                        recurringCycle = cycleNumber,
                        legs = listOf(
                            TransactionLeg(
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
                transactionRepository.createTransaction(
                    TransactionIntent(
                        title = recurring.title,
                        date = date,
                        category = recurring.category,
                        recurringId = recurring.id,
                        recurringCycle = cycleNumber,
                        legs = listOf(
                            TransactionLeg(
                                type = recurring.type,
                                amount = amount,
                                category = recurring.category,
                                account = sourceAccount,
                            )
                        ),
                    )
                )
            }
        }.flatMap { transaction ->
            catch {
                recurringOccurrenceRepository.save(
                    RecurringOccurrence(
                        recurringId = recurring.id,
                        cycleNumber = cycleNumber,
                        yearMonth = yearMonth,
                        status = RecurringOccurrence.Status.CONFIRMED,
                        transactionId = transaction.id,
                        effectiveDate = date,
                        handledAt = Clock.System.now().toEpochMilliseconds(),
                    )
                )
                transaction
            }
        }
    }
}
