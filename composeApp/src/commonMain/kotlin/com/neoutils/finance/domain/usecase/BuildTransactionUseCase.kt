@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.errors.BuildTransactionErrors
import com.neoutils.finance.domain.exception.BuildTransactionException
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.model.form.TransactionForm
import com.neoutils.finance.extension.moneyToDouble
import com.neoutils.finance.util.DateFormats
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class BuildTransactionUseCase(
    private val getOrCreateInvoiceForMonthUseCase: GetOrCreateInvoiceForMonthUseCase
) {
    private val currentDate
        get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    private val formats = DateFormats()
    private val errors = BuildTransactionErrors()

    suspend operator fun invoke(
        form: TransactionForm,
        id: Long = 0
    ): Result<Transaction> {
        if (form.amount.isEmpty()) {
            return Result.failure(BuildTransactionException(errors.amountRequired))
        }

        if (form.amount.moneyToDouble() == 0.0) {
            return Result.failure(BuildTransactionException(errors.amountZero))
        }

        if (form.date.isEmpty()) {
            return Result.failure(BuildTransactionException(errors.dateRequired))
        }

        if (form.title.isNullOrEmpty() && form.category == null) {
            return Result.failure(BuildTransactionException(errors.titleOrCategoryRequired))
        }

        val date = runCatching {
            formats.dayMonthYear.parse(form.date)
        }.getOrElse {
            return Result.failure(BuildTransactionException(errors.dateInvalid))
        }

        if (date > currentDate) {
            return Result.failure(BuildTransactionException(errors.dateFuture))
        }

        if (form.target.isAccount) {
            return Result.success(
                Transaction(
                    id = id,
                    type = form.type,
                    amount = form.amount.moneyToDouble(),
                    title = form.title,
                    date = date,
                    category = form.category,
                    target = form.target,
                    creditCard = null,
                    invoice = null,
                )
            )
        }

        if (form.type != Transaction.Type.EXPENSE) {
            return Result.failure(BuildTransactionException(errors.creditCardExpenseOnly))
        }

        val creditCard = form.creditCard
            ?: return Result.failure(BuildTransactionException(errors.creditCardRequired))

        val dueMonth = form.invoiceDueMonth
            ?: return Result.failure(BuildTransactionException(errors.invoiceRequired))

        val invoice = getOrCreateInvoiceForMonthUseCase(creditCard, dueMonth).getOrElse {
            return Result.failure(it)
        }

        if (invoice.status.isClosed) {
            return Result.failure(BuildTransactionException(errors.closedInvoice))
        }

        if (invoice.status.isPaid) {
            return Result.failure(BuildTransactionException(errors.closedInvoice))
        }

        return Result.success(
            Transaction(
                id = id,
                type = form.type,
                amount = form.amount.moneyToDouble(),
                title = form.title,
                date = date,
                category = form.category,
                target = form.target,
                creditCard = creditCard,
                invoice = invoice,
            )
        )
    }
}
