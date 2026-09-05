package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.IAccountRepository
import kotlinx.datetime.LocalDate

/**
 * The invoices that accept this are the ones still taking spending — `OPEN` and
 * `RETROACTIVE` — because only an invoice without a final figure can be paid in part.
 * `Invoice.acceptsPartialPayment` is the owner of that rule, and
 * [ValidateAdvanceInvoicePaymentUseCase] is what makes it a permission and not merely an offer:
 * it owns every rule this operation is admissible by, and [UpdateAdvanceInvoicePaymentUseCase]
 * reads the same one, so registering a payment and correcting one cannot drift apart.
 *
 * What the payment looks like in the ledger is [WriteInvoicePaymentUseCase]'s, and the
 * correction states the same two legs by reading the same owner.
 */
class AdvanceInvoicePaymentUseCaseImpl(
    private val writeInvoicePayment: WriteInvoicePaymentUseCase,
    private val validateInvoicePayment: ValidateAdvanceInvoicePaymentUseCase,
    private val accountRepository: IAccountRepository,
) : AdvanceInvoicePaymentUseCase {

    override suspend fun invoke(
        invoiceId: Long,
        amount: Double,
        date: LocalDate,
        accountId: Long,
        paidAmount: Double?,
    ): Either<Throwable, Transaction> = either {
        // No operation to leave out of the ceiling: this one does not exist yet.
        val (invoice) = validateInvoicePayment(
            invoiceId = invoiceId,
            amount = amount,
            date = date,
            paidAmount = paidAmount,
        ).mapLeft { InvoiceException(it) }.bind()

        // The paying account is resolved here, and not merely handed in, because the
        // currency the rate is harvested against is the one it carries *now*.
        val account = ensureNotNull(
            catch { accountRepository.getAccountById(accountId) }.bind()
        ) {
            AccountException(AccountError.NOT_FOUND)
        }

        catch {
            writeInvoicePayment(
                invoice = invoice,
                account = account,
                leaving = paidAmount ?: amount,
                settling = amount,
                date = date,
            )
        }.bind()
    }
}
