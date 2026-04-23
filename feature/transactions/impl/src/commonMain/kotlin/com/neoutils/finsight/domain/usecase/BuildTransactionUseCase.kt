@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.BuildTransactionError
import com.neoutils.finsight.domain.exception.BuildTransactionException
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.extension.moneyToDouble
import com.neoutils.finsight.util.dayMonthYear
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val currentDate
    get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

class BuildTransactionUseCase(
    private val getOrCreateInvoiceForMonth: IGetOrCreateInvoiceForMonthUseCase
) : IBuildTransactionUseCase {

    override suspend operator fun invoke(
        form: TransactionForm,
        id: Long,
        operationId: Long?,
    ): Either<Throwable, Transaction> = either {
        ensure(form.amount.isNotEmpty()) {
            BuildTransactionException(BuildTransactionError.AmountRequired)
        }

        ensure(form.amount.moneyToDouble() != 0.0) {
            BuildTransactionException(BuildTransactionError.AmountZero)
        }

        ensure(form.date.isNotEmpty()) {
            BuildTransactionException(BuildTransactionError.DateRequired)
        }

        ensure(!form.title.isNullOrEmpty() || form.category != null) {
            BuildTransactionException(BuildTransactionError.TitleOrCategoryRequired)
        }

        val date = catch { dayMonthYear.parse(form.date) }.bind()

        ensure(date <= currentDate) {
            BuildTransactionException(BuildTransactionError.DateFuture)
        }

        if (form.target.isAccount) {

            ensureNotNull(form.account) {
                BuildTransactionException(BuildTransactionError.AccountRequired)
            }

            return@either Transaction(
                id = id,
                operationId = operationId,
                type = form.type,
                amount = form.amount.moneyToDouble(),
                title = form.title,
                date = date,
                category = form.category,
                target = form.target,
                account = form.account,
                creditCard = null,
                invoice = null,
            )
        }

        ensure(form.type == Transaction.Type.EXPENSE) {
            BuildTransactionException(BuildTransactionError.CreditCardExpenseOnly)
        }

        val creditCard = form.creditCard
        ensureNotNull(creditCard) {
            BuildTransactionException(BuildTransactionError.CreditCardRequired)
        }

        val invoiceDueMonth = form.invoiceDueMonth
        ensureNotNull(invoiceDueMonth) {
            BuildTransactionException(BuildTransactionError.InvoiceRequired)
        }

        val invoice = getOrCreateInvoiceForMonth(creditCard, invoiceDueMonth).bind()

        ensure(!invoice.status.isClosed) {
            BuildTransactionException(BuildTransactionError.ClosedInvoice)
        }

        ensure(!invoice.status.isPaid) {
            BuildTransactionException(BuildTransactionError.ClosedInvoice)
        }

        Transaction(
            id = id,
            operationId = operationId,
            type = form.type,
            amount = form.amount.moneyToDouble(),
            title = form.title,
            date = date,
            category = form.category,
            target = form.target,
            creditCard = form.creditCard,
            invoice = invoice,
        )
    }
}
