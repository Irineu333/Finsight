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
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.extension.today
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class PayInvoicePaymentUseCaseImpl(
    private val clock: Clock,
    private val validateInvoicePayment: ValidateInvoicePaymentUseCase,
    private val writeInvoicePayment: WriteInvoicePaymentUseCase,
    private val invoiceRepository: IInvoiceRepository,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
    private val payInvoiceUseCase: PayInvoiceUseCase,
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

        // The domain owns which invoices are discharged by paying them; enumerating
        // the status here would be a second copy of that rule.
        ensure(invoice.acceptsFullSettlement) {
            InvoiceException(InvoiceError.InvoiceNotClosed)
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
        // It is the same derivation `PayInvoiceUseCase` consults, not a copy, so what refuses the
        // payment here and what refuses the discharge below cannot drift into disagreeing. What it
        // adds to the guard above is the *date*: which invoices are discharged by paying them is
        // `acceptsFullSettlement`'s, and when one may be paid is this.
        validateInvoicePayment(invoice = invoice, date = date, today = clock.today())
            .mapLeft(::InvoiceException)
            .bind()

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
            writeInvoicePayment(
                invoice = invoice,
                account = account,
                leaving = leaving,
                settling = currentBillAmount,
                date = date,
            )
        }.bind()

        payInvoiceUseCase(
            invoiceId = invoiceId,
            paidAt = date,
        ).bind()
    }
}
