@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.model.form

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.extension.isAccept
import com.neoutils.finsight.extension.moneyToDouble
import com.neoutils.finsight.util.dayMonthYear
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val currentDate
    get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

data class TransactionForm(
    val type: TransactionType,
    val amount: String,
    val title: String?,
    val date: String,
    val category: Category?,
    val target: TransactionTarget,
    val creditCard: CreditCard?,
    val invoiceDueMonth: YearMonth?,
    val account: Account?,
    val installments: Int = 1
) {
    fun isValid(): Boolean {
        if (amount.isEmpty()) return false
        if (amount.moneyToDouble() == 0.0) return false
        if (date.isEmpty()) return false
        if (title.isNullOrEmpty() && category == null) return false

        val date = runCatching {
            dayMonthYear.parse(date)
        }.getOrElse { return false }

        if (date > currentDate) return false

        if (target.isAccount) return account != null

        if (type != TransactionType.EXPENSE) return false
        if (creditCard == null) return false

        return true
    }

    companion object {
        fun from(
            type: TransactionType,
            amount: String,
            title: String?,
            date: String,
            category: Category?,
            target: TransactionTarget,
            creditCard: CreditCard?,
            invoiceDueMonth: YearMonth?,
            account: Account?,
            installments: Int = 1
        ): TransactionForm {

            val target = target.takeIf { type.isExpense } ?: TransactionTarget.ACCOUNT
            val category = category?.takeIf { it.type.isAccept(type) }
            val creditCard = creditCard?.takeIf { target.isCreditCard }
            val account = account?.takeIf { target.isAccount }
            val installments = installments.takeIf { target.isCreditCard } ?: 1

            return TransactionForm(
                type = type,
                amount = amount,
                title = title?.ifEmpty { null },
                date = date,
                category = category,
                target = target,
                creditCard = creditCard,
                invoiceDueMonth = invoiceDueMonth,
                account = account,
                installments = installments,
            )
        }
    }
}
