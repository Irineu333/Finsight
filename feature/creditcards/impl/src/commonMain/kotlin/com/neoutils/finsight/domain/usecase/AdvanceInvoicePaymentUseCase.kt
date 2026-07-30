@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val currentDate
    get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

/**
 * Pays part of an open invoice ahead of time.
 *
 * **[amount] is in the card's currency and always has been**, and that is what makes the
 * ceiling below correct: `amount <= currentBillAmount` compares two figures denominated
 * the same way. When the paying account is denominated differently the caller adds
 * [paidAmount], which is what leaves the *account* — and that side carries no ceiling at
 * all, because comparing it to the invoice would be comparing two currencies.
 */
class AdvanceInvoicePaymentUseCase(
    private val transactionRepository: ITransactionRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
    private val harvestExchangeRate: HarvestExchangeRateUseCase,
    private val accountRepository: IAccountRepository,
) {
    /**
     * @param amount how much of the invoice is being settled, in the **card's** currency.
     * @param paidAmount what leaves [account], when it is denominated differently.
     * `null` is the same-currency case, unchanged.
     */
    suspend operator fun invoke(
        invoiceId: Long,
        amount: Double,
        date: LocalDate,
        account: Account,
        paidAmount: Double? = null,
    ): Either<Throwable, Transaction> = either {
        ensure(amount > 0) {
            InvoiceException(InvoiceError.NegativeAmount)
        }

        ensure(paidAmount == null || paidAmount > 0) {
            InvoiceException(InvoiceError.NegativeAmount)
        }

        val invoice = invoiceRepository.getInvoiceById(invoiceId)

        ensureNotNull(invoice) {
            InvoiceException(InvoiceError.NotFound)
        }

        ensure(date >= invoice.openingDate && date <= invoice.closingDate) {
            InvoiceException(InvoiceError.DateOutsideInvoicePeriod)
        }

        ensure(date <= currentDate) {
            InvoiceException(InvoiceError.DateInFuture)
        }

        val currentBillAmount = calculateInvoiceUseCase(invoice)

        ensure(currentBillAmount > 0.0) {
            InvoiceException(InvoiceError.InvoiceNotInDebt)
        }

        // The ceiling holds over the card's side only. The account's side is free,
        // because a limit on it would be a limit expressed in the wrong currency.
        ensure(amount <= currentBillAmount) {
            InvoiceException(InvoiceError.AmountExceedsInvoice)
        }

        val leaving = paidAmount ?: amount

        val transaction = catch {
            transactionRepository.createTransaction(
                TransactionIntent(
                    title = null,
                    date = date,
                    legs = listOf(
                        // The money leaves the account undimensioned; only the card's
                        // leg carries the invoice's sub-ledger, or the two would
                        // cancel it out.
                        TransactionLeg(
                            type = TransactionType.EXPENSE,
                            amount = leaving,
                            accountId = account.id,
                        ),
                        TransactionLeg(
                            type = TransactionType.INCOME,
                            amount = amount,
                            accountId = invoice.creditCard.accountId,
                            dimensionId = invoice.dimensionId,
                        ),
                    ),
                )
            )
        }.bind()

        // The rate this payment applied, written to the archive and never to the
        // transaction (design D11).
        catch {
            val cardCurrency = accountRepository.getAccountById(invoice.creditCard.accountId)?.currency
            if (cardCurrency != null) {
                harvestExchangeRate(
                    sourceAmount = leaving,
                    sourceCurrency = account.currency,
                    targetAmount = amount,
                    targetCurrency = cardCurrency,
                    date = date,
                )
            }
        }

        transaction
    }
}
