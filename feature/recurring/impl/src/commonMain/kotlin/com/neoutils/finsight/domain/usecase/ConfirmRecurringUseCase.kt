@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.flatMap
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
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.extension.accountType
import com.neoutils.finsight.extension.monthsUntil
import com.neoutils.finsight.extension.toYearMonth
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * How the writer completes the recurring's single leg: on the nominal of its
 * category's nature, tagged with that category's dimension. Same rule as a manual
 * transaction — the template just carries the choice instead of a form.
 */
private fun Recurring.contraLeg(): ContraLeg = when (type) {
    TransactionType.ADJUSTMENT -> ContraLeg(AccountType.EQUITY)
    TransactionType.EXPENSE -> ContraLeg(category?.type?.accountType ?: AccountType.EXPENSE, category?.dimensionId)
    TransactionType.INCOME -> ContraLeg(category?.type?.accountType ?: AccountType.INCOME, category?.dimensionId)
}

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
                        contra = recurring.contraLeg(),
                    )
                )
            } else {
                val sourceAccount = account ?: recurring.account
                transactionRepository.createTransaction(
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
                        contra = recurring.contraLeg(),
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
