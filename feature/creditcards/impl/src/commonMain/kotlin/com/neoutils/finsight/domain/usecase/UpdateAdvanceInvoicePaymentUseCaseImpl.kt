package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.datetime.LocalDate

/**
 * [ValidateAdvanceInvoicePaymentUseCase] owns the rules, and this reads the same ones
 * [AdvanceInvoicePaymentUseCase] reads — including `acceptsPartialPayment`, which is what
 * keeps a correction from reaching an invoice a registration could not.
 *
 * What the payment looks like in the ledger is [WriteInvoicePaymentUseCase]'s, in its
 * rewriting form: same two legs, same owner, and the transaction keeps its identity.
 */
class UpdateAdvanceInvoicePaymentUseCaseImpl(
    private val writeInvoicePayment: WriteInvoicePaymentUseCase,
    private val validateInvoicePayment: ValidateAdvanceInvoicePaymentUseCase,
    private val transactionRepository: ITransactionRepository,
    private val accountRepository: IAccountRepository,
) : UpdateAdvanceInvoicePaymentUseCase {

    override suspend fun invoke(
        transactionId: Long,
        invoiceId: Long,
        amount: Double,
        date: LocalDate,
        accountId: Long,
        paidAmount: Double?,
    ): Either<Throwable, Unit> = either {
        // The operation leaves its own contribution out of the ceiling: it already
        // reduced what the invoice owes, and a ceiling counting it would refuse the
        // correction that raises the figure. On an invoice it never touched — the
        // correction that switched invoices — there is nothing to leave out.
        val (invoice) = validateInvoicePayment(
            invoiceId = invoiceId,
            amount = amount,
            date = date,
            paidAmount = paidAmount,
            excluding = transactionId,
        ).mapLeft { InvoiceException(it) }.bind()

        // The paying account is resolved here, and not merely handed in, because the
        // currency the rate is harvested against is the one it carries *now*.
        val account = ensureNotNull(
            catch { accountRepository.getAccountById(accountId) }.bind()
        ) {
            AccountException(AccountError.NOT_FOUND)
        }

        // Read to refuse correcting an operation that is not there — and for the one
        // thing carried forward from it: the title, which this form does not exhibit
        // and therefore must not erase.
        val transaction = transactionRepository.getTransactionById(transactionId)

        ensureNotNull(transaction) { InvoiceException(InvoiceError.NotFound) }

        catch {
            writeInvoicePayment.rewrite(
                transactionId = transaction.id,
                title = transaction.title,
                invoice = invoice,
                account = account,
                leaving = paidAmount ?: amount,
                settling = amount,
                date = date,
            )
        }.bind()
    }
}
