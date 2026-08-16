@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.getOrElse
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.error.RecurringError
import com.neoutils.finsight.domain.exception.RecurringException
import com.neoutils.finsight.domain.extension.currencyOf
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.extension.contraLegFor
import com.neoutils.finsight.extension.monthsUntil
import com.neoutils.finsight.extension.toYearMonth
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class ConfirmRecurringUseCaseImpl(
    private val recurringRepository: IRecurringRepository,
    private val recurringOccurrenceRepository: IRecurringOccurrenceRepository,
    private val getOrCreateInvoiceForMonthUseCase: GetOrCreateInvoiceForMonthUseCase,
    private val accountRepository: IAccountRepository,
) : ConfirmRecurringUseCase {

    override suspend fun invoke(
        recurringId: Long,
        date: LocalDate,
        amount: Double?,
        target: TransactionTarget?,
        account: Account?,
        creditCard: CreditCard?,
        invoice: Invoice?,
        title: String?,
        category: Category?,
    ): Either<Throwable, Transaction> = catch {
        // Resolved here and not received: the cycle is posted against the template as it
        // is at this instant, so a sheet opened minutes ago cannot confirm against an
        // amount, a destination or a `createdAt` that has since changed.
        val recurring = recurringRepository.getRecurringById(recurringId)
            ?: throw RecurringException(RecurringError.NOT_FOUND)

        val yearMonth = date.yearMonth
        val cycleNumber = Instant
            .fromEpochMilliseconds(recurring.createdAt)
            .toYearMonth()
            .monthsUntil(yearMonth) + 1

        // Omitting one of these asks for the cycle the template describes, and the
        // template is what answers. Resolved here rather than announced as a default on
        // the signature: a parameter default read off the aggregate is only expressible
        // while the aggregate is in the signature, and it makes every caller responsible
        // for knowing a rule that belongs to this operation.
        val cycleAmount = amount ?: recurring.amount
        val cycleTarget = target ?: recurring.ownTarget

        // Blank is an absence, not the template's title: a transaction with no title of
        // its own is displayed by its category, which is the rule the whole app reads
        // titles by (`displayTitleOf`). Falling back to the template here would hand the
        // user a name they had just erased — so the fallback does not exist, for anyone.
        val cycleTitle = title?.trim()?.takeIf { it.isNotBlank() }

        // The template's own denomination, and the one the confirmation has to land
        // in. `null` means the template names no account any more — there is nothing
        // to disagree with, so the check has nothing to say.
        val templateCurrency = accountRepository.currencyOf(recurring)

        val intent = if (cycleTarget.isCreditCard) {
            val targetCreditCard = creditCard ?: recurring.creditCard
            requireNotNull(targetCreditCard) { "Credit card is required for recurring confirmation" }

            accountRepository.rejectIfCurrencyDiffers(
                templateCurrency = templateCurrency,
                targetCurrency = accountRepository.currencyOf(targetCreditCard),
            )

            // Deliberately outside the unit of work below: an invoice created here
            // and left unused is a smaller harm than undoing invoice structure —
            // and far smaller than a duplicated ledger entry (design D7).
            val targetInvoice = invoice
                ?: getOrCreateInvoiceForMonthUseCase(targetCreditCard, yearMonth)
                    .getOrElse { throw it }

            TransactionIntent(
                title = cycleTitle,
                date = date,
                recurringId = recurring.id,
                recurringCycle = cycleNumber,
                legs = listOf(
                    TransactionLeg(
                        type = recurring.type,
                        amount = cycleAmount,
                        accountId = targetCreditCard.accountId,
                        dimensionId = targetInvoice.dimensionId,
                    )
                ),
                contra = contraLegFor(recurring.type, category),
            )
        } else {
            val sourceAccount = account ?: recurring.account

            accountRepository.rejectIfCurrencyDiffers(
                templateCurrency = templateCurrency,
                targetCurrency = sourceAccount?.currency,
            )

            TransactionIntent(
                title = cycleTitle,
                date = date,
                recurringId = recurring.id,
                recurringCycle = cycleNumber,
                legs = listOf(
                    TransactionLeg(
                        type = recurring.type,
                        amount = cycleAmount,
                        accountId = requireNotNull(sourceAccount) {
                            "Account is required for recurring confirmation"
                        }.id,
                    )
                ),
                contra = contraLegFor(recurring.type, category),
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

/** Where the template itself posts: the card it names, or else an account. */
private val Recurring.ownTarget: TransactionTarget
    get() = if (creditCard != null) TransactionTarget.CREDIT_CARD else TransactionTarget.ACCOUNT

/**
 * Refuses a confirmation aimed at a currency other than the template's.
 *
 * A `null` on either side is not a disagreement: a template whose account is gone, or a
 * card whose ledger account cannot be resolved, is a different failure with a different
 * message, and this guard has nothing to add to it.
 */
private fun IAccountRepository.rejectIfCurrencyDiffers(
    templateCurrency: String?,
    targetCurrency: String?,
) {
    if (templateCurrency == null || targetCurrency == null) return
    if (templateCurrency != targetCurrency) throw RecurringException(RecurringError.CURRENCY_MISMATCH)
}
