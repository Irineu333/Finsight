@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.model.form

import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.extension.isAccept
import com.neoutils.finance.util.DateFormats
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val currentDate
    get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

private val formats = DateFormats()

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
    fun build(id: Long = 0): Transaction {

        check(isValid()) { "Invalid Transaction" }

        return Transaction(
            id = id,
            type = type,
            amount = parseMoneyToDouble(amount),
            title = title,
            date = formats.dayMonthYear.parse(date),
            category = category,
            target = target,
            creditCard = creditCard,
            invoice = invoice,
        )
    }

    fun isValid(): Boolean {

        if (amount.isEmpty()) return false

        if (parseMoneyToDouble(amount) == 0.0) return false

        if (date.isEmpty()) return false

        if (title.isNullOrEmpty() && category == null) return false

        val parsedDate = runCatching { formats.dayMonthYear.parse(date) }.getOrElse { return false }

        // Não pode ser no futuro
        if (parsedDate > currentDate) return false

        if (target.isAccount) return true

        // Credit Card

        if (type != Transaction.Type.EXPENSE) return false

        val invoice = invoice ?: return false
        val creditCard = creditCard ?: return false

        if (invoice.status != Invoice.Status.OPEN) return false

        if (creditCard.id != invoice.creditCard.id) return false

        if (parsedDate < invoice.openingDate) return false
        if (parsedDate > invoice.closingDate) return false

        return true
    }

    private fun parseMoneyToDouble(formatted: String): Double {
        val digitsOnly = formatted
            .replace("R$", "")
            .replace(".", "")
            .replace(",", ".")
            .trim()

        return digitsOnly.toDoubleOrNull() ?: 0.0
    }

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