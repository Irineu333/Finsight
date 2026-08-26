@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.getOrElse
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
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
import com.neoutils.finsight.domain.error.RecurringError
import com.neoutils.finsight.domain.exception.RecurringException
import com.neoutils.finsight.domain.extension.currencyOf
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.extension.contraLegFor
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Confirms one cycle of a recurring, optionally redirecting it to another account or
 * card and overriding what that cycle is called and how it is classified.
 *
 * **Every override applies to the confirmed cycle alone.** The template is read, never
 * written: a cycle that was in fact something else — another title, another category —
 * is a fact about that month, not a correction of the model. Whoever wants to change
 * the model edits the recurring.
 *
 * **A redirection to a different currency is refused, never converted** (design D17).
 * This is the one place a facade value could be written down as if it were another
 * currency: the amount defaults to `recurring.amount`, and confirming a template created
 * on a BRL account against a USD one would record the raw number as dollars. Converting
 * instead would mean picking a rate on the user's behalf, mid-confirmation, in a
 * decision they neither asked for nor can see.
 *
 * The selector is what makes this unreachable by the designed path — it offers only
 * accounts of the template's own currency. This is the net behind it.
 */
class ConfirmRecurringUseCase(
    private val recurringOccurrenceRepository: IRecurringOccurrenceRepository,
    private val getOrCreateInvoiceForMonthUseCase: GetOrCreateInvoiceForMonthUseCase,
    private val accountRepository: IAccountRepository,
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
        title: String? = recurring.title,
        category: Category? = recurring.category,
    ): Either<Throwable, Transaction> {
        val yearMonth = date.yearMonth

        return catch {
            // Which cycle this is, asked of the template rather than counted here. The
            // answer is absent for a month before the series began, and that is a
            // refusal and not a zero: an occurrence numbered 0 was persisted and read
            // back onto the screen as "Aluguel • 0".
            //
            // First, so that nothing is written before it. The date picker of the
            // confirmation is what makes this unreachable by the designed path; this is
            // the net behind it, like the currency refusal below.
            val cycleNumber = requireNotNull(recurring.cycleNumberIn(yearMonth)) {
                "Recurring ${recurring.id} has no cycle in $yearMonth: " +
                    "its series begins in ${recurring.originMonth}"
            }

            // The template's own denomination, and the one the confirmation has to land
            // in. `null` means the template names no account any more — there is nothing
            // to disagree with, so the check has nothing to say.
            val templateCurrency = accountRepository.currencyOf(recurring)

            val intent = if (target.isCreditCard) {
                val targetCreditCard = creditCard ?: recurring.creditCard
                requireNotNull(targetCreditCard) { "Credit card is required for recurring confirmation" }

                accountRepository.rejectIfCurrencyDiffers(
                    templateCurrency = templateCurrency,
                    targetCurrency = accountRepository.currencyOf(targetCreditCard),
                )

                // Deliberately outside the unit of work below: an invoice created here
                // and left unused is a smaller harm than undoing invoice structure —
                // and far smaller than a duplicated ledger entry (design D7).
                val invoice = invoice
                    ?: getOrCreateInvoiceForMonthUseCase(targetCreditCard, yearMonth)
                        .getOrElse { throw it }

                TransactionIntent(
                    title = title,
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
                    contra = contraLegFor(recurring.type, category),
                )
            } else {
                val sourceAccount = account ?: recurring.account

                accountRepository.rejectIfCurrencyDiffers(
                    templateCurrency = templateCurrency,
                    targetCurrency = sourceAccount?.currency,
                )

                TransactionIntent(
                    title = title,
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
}

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
