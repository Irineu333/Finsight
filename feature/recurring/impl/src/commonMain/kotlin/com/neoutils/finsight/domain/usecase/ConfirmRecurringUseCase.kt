@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.getOrElse
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.extension.contraLegFor
import com.neoutils.finsight.extension.monthsUntil
import com.neoutils.finsight.extension.toYearMonth
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class ConfirmRecurringUseCase(
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
            val intent = if (target.isCreditCard) {
                val targetCreditCard = creditCard ?: recurring.creditCard
                requireNotNull(targetCreditCard) { "Credit card is required for recurring confirmation" }

                // Deliberately outside the unit of work below: an invoice created here
                // and left unused is a smaller harm than undoing invoice structure —
                // and far smaller than a duplicated ledger entry (design D7).
                val invoice = invoice
                    ?: getOrCreateInvoiceForMonthUseCase(targetCreditCard, yearMonth)
                        .getOrElse { throw it }

                TransactionIntent(
                    title = recurring.title,
                    date = date,
                    recurringId = recurring.id,
                    recurringCycle = cycleNumber,
                    legs = listOf(
                        TransactionLeg(
                            type = recurring.type,
                            amount = amount,
                            accountId = targetCreditCard.accountId,
                            dimensionId = invoice.dimensionId,
                        )
                    ),
                    contra = contraLegFor(recurring.type, recurring.category),
                )
            } else {
                val sourceAccount = account ?: recurring.account
                TransactionIntent(
                    title = recurring.title,
                    date = date,
                    recurringId = recurring.id,
                    recurringCycle = cycleNumber,
                    legs = listOf(
                        TransactionLeg(
                            type = recurring.type,
                            amount = amount,
                            accountId = requireNotNull(sourceAccount) {
                                "Account is required for recurring confirmation"
                            }.id,
                        )
                    ),
                    contra = contraLegFor(recurring.type, recurring.category),
                )
            }

            // Transaction, re-entry check and occurrence are a single unit of work;
            // no dispatcher switch may come between them (design D7).
            recurringOccurrenceRepository.confirmCycle(
                intent = intent,
                occurrence = RecurringOccurrence(
                    recurringId = recurring.id,
                    cycleNumber = cycleNumber,
                    yearMonth = yearMonth,
                    status = RecurringOccurrence.Status.CONFIRMED,
                    effectiveDate = date,
                    handledAt = Clock.System.now().toEpochMilliseconds(),
                ),
            )
        }
    }
}
