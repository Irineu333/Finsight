@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.extension.paymentObstacleOn
import com.neoutils.finsight.extension.today
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class PayInvoicePaymentUseCaseImpl(
    private val clock: Clock,
    private val transactionRepository: ITransactionRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
    private val payInvoiceUseCase: PayInvoiceUseCase,
    private val harvestExchangeRate: HarvestExchangeRateUseCase,
    private val accountRepository: IAccountRepository,
) : PayInvoicePaymentUseCase {

    override suspend fun invoke(
        invoiceId: Long,
        date: LocalDate,
        accountId: Long,
        paidAmount: Double?,
        // Throwable, not InvoiceException: `createTransaction` throws from the write
        // boundary (e.g. ClosedAccountException if the paying account is archived
        // mid-flight), and `either {}` does not intercept a thrown exception — only a
        // Raise. Left untyped it escaped the Either and could crash the caller.
        // Wrapping it in `catch {}.bind()` — as AdvanceInvoicePaymentUseCase already
        // does — turns it into the Left the ViewModel maps to a message.
    ): Either<Throwable, Invoice> = either {
        val invoice = invoiceRepository.getInvoiceById(invoiceId)

        ensureNotNull(invoice) {
            InvoiceException(InvoiceError.NotFound)
        }

        // The paying account is resolved here, and not merely handed in, because the
        // currency the rate is harvested against is the one it carries *now*.
        val account = ensureNotNull(
            catch { accountRepository.getAccountById(accountId) }.bind()
        ) {
            AccountException(AccountError.NOT_FOUND)
        }

        // **Asked before anything is written, and this order is the rule.** Marking the invoice
        // paid is a second write, and what refuses the payment used to be discovered inside it —
        // after the posting had already taken the money out. There is no compensating write, so
        // the account came up short by a payment the app reported as refused, and the log recorded
        // it as such. Reading the obstacle here is what makes a refusal mean nothing happened.
        //
        // It is the same derivation `PayInvoiceUseCase` consults, not a copy: the status this used
        // to check by hand was narrower than the domain's own answer, and rejected a retroactive
        // invoice the ledger is willing to settle.
        invoice.paymentObstacleOn(date = date, today = clock.today())?.let {
            raise(InvoiceException(it))
        }

        val currentBillAmount = calculateInvoiceUseCase(invoice)

        ensure(currentBillAmount > 0.0) {
            InvoiceException(InvoiceError.InvoiceNotInDebt)
        }

        // What the invoice owes is not negotiable; what the account gives up is, and
        // only when the two are denominated differently.
        val leaving = paidAmount ?: currentBillAmount

        ensure(leaving > 0.0) {
            InvoiceException(InvoiceError.InvoiceNotInDebt)
        }

        catch {
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
                            amount = currentBillAmount,
                            accountId = invoice.creditCard.accountId,
                            dimensionId = invoice.dimensionId,
                        ),
                    ),
                )
            )
        }.bind()

        // The rate the payment applied, learned from its own two ends. It is written to
        // the archive and never to the transaction, and it survives the transaction
        // being deleted (design D11, D27).
        catch {
            val cardCurrency = accountRepository.getAccountById(invoice.creditCard.accountId)?.currency
            if (cardCurrency != null) {
                harvestExchangeRate(
                    sourceAmount = leaving,
                    sourceCurrency = account.currency,
                    targetAmount = currentBillAmount,
                    targetCurrency = cardCurrency,
                    date = date,
                )
            }
        }

        payInvoiceUseCase(
            invoiceId = invoiceId,
            paidAt = date,
        ).bind()
    }
}
