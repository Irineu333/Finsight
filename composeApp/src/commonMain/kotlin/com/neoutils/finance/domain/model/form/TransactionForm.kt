@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.model.form

import com.neoutils.finance.domain.errors.BuildTransactionErrors
import com.neoutils.finance.domain.exception.BuildTransactionException
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.extension.isAccept
import com.neoutils.finance.extension.moneyToDouble
import com.neoutils.finance.util.DateFormats
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val currentDate
    get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

private val formats = DateFormats()

private val errors = BuildTransactionErrors()

data class TransactionForm(
    val type: Transaction.Type,
    val amount: String,
    val title: String?,
    val date: String,
    val category: Category?,
    val target: Transaction.Target,
    val creditCard: CreditCard?,
    val invoice: Invoice?
) {
    fun build(id: Long = 0): Result<Transaction> {

        if (amount.isEmpty()) {
            return Result.failure(BuildTransactionException(errors.amountRequired))
        }

        if (amount.moneyToDouble() == 0.0) {
            return Result.failure(BuildTransactionException(errors.amountZero))
        }

        if (date.isEmpty()) {
            return Result.failure(BuildTransactionException(errors.dateRequired))
        }

        if (title.isNullOrEmpty() && category == null) {
            return Result.failure(BuildTransactionException(errors.titleOrCategoryRequired))
        }

        val date = runCatching {
            formats.dayMonthYear.parse(date)
        }.getOrElse {
            return Result.failure(BuildTransactionException(errors.dateInvalid))
        }

        if (date > currentDate) {
            return Result.failure(BuildTransactionException(errors.dateFuture))
        }

        if (target.isAccount) {
            return Result.success(
                Transaction(
                    id = id,
                    type = type,
                    amount = amount.moneyToDouble(),
                    title = title,
                    date = date,
                    category = category,
                    target = target,
                    creditCard = null,
                    invoice = null,
                )
            )
        }

        if (type != Transaction.Type.EXPENSE) {
            return Result.failure(BuildTransactionException(errors.creditCardExpenseOnly))
        }

        val invoice = invoice ?: return Result.failure(
            BuildTransactionException(errors.invoiceRequired)
        )

        val creditCard = creditCard ?: return Result.failure(
            BuildTransactionException(errors.creditCardRequired)
        )

        if (invoice.status != Invoice.Status.OPEN) {
            return Result.failure(BuildTransactionException(errors.invoiceNotOpen))
        }

        if (creditCard.id != invoice.creditCard.id) {
            return Result.failure(BuildTransactionException(errors.creditCardMismatch))
        }

        if (date < invoice.openingDate || date > invoice.closingDate) {
            return Result.failure(BuildTransactionException(errors.dateOutsideInvoicePeriod))
        }

        return Result.success(
            Transaction(
                id = id,
                type = type,
                amount = amount.moneyToDouble(),
                title = title,
                date = date,
                category = category,
                target = target,
                creditCard = creditCard,
                invoice = invoice,
            )
        )
    }

    fun isValid() = build().isSuccess

    companion object {
        fun from(
            type: Transaction.Type,
            amount: String,
            title: String?,
            date: String,
            category: Category?,
            target: Transaction.Target,
            creditCard: CreditCard?,
            invoice: Invoice?,
        ): TransactionForm {

            val target = target.takeIf { type.isExpense } ?: Transaction.Target.ACCOUNT
            val invoice = invoice.takeIf { target.isCreditCard }
            val category = category?.takeIf { it.type.isAccept(type) }
            val creditCard = creditCard?.takeIf { target.isCreditCard }

            return TransactionForm(
                type = type,
                amount = amount,
                title = title?.ifEmpty { null },
                date = date,
                category = category,
                target = target,
                creditCard = creditCard,
                invoice = invoice,
            )
        }
    }
}
