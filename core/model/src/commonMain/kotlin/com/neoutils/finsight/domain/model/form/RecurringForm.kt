package com.neoutils.finsight.domain.model.form

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.RecurringError
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.extension.isAccept
import com.neoutils.finsight.extension.moneyToDouble

data class RecurringForm(
    val type: TransactionType,
    val amount: String,
    val title: String,
    val dayOfMonth: String,
    val account: Account?,
    val creditCard: CreditCard?,
    val category: Category?,
) {
    /**
     * The validated template this form describes, or the first rule it breaks.
     *
     * The single owner of "what a recurring has to satisfy to exist". [isValid] is the
     * cheap reading of the same rules for the UI, and every caller that persists a
     * template goes through here — a second hand-written copy of the checks is what
     * this replaces.
     *
     * [createdAt] is the anchor the cycle numbering is counted from, and it is the
     * caller's to decide: the clock, for a template created from the recurring form;
     * the transaction's own date, for one born out of a transaction.
     */
    fun toRecurring(createdAt: Long): Either<RecurringError, Recurring> = either {
        ensure(amount.isNotEmpty()) { RecurringError.AMOUNT_REQUIRED }
        ensure(amount.moneyToDouble() != 0.0) { RecurringError.AMOUNT_ZERO }
        ensure(title.isNotEmpty() || category != null) {
            RecurringError.TITLE_OR_CATEGORY_REQUIRED
        }

        val day = ensureNotNull(dayOfMonth.toIntOrNull()) { RecurringError.INVALID_DAY }
        ensure(day in 1..31) { RecurringError.INVALID_DAY }

        if (type.isIncome) {
            ensureNotNull(account) { RecurringError.ACCOUNT_REQUIRED }
        } else {
            ensure(account != null || creditCard != null) { RecurringError.ACCOUNT_REQUIRED }
        }

        Recurring(
            type = type,
            amount = amount.moneyToDouble(),
            title = title.ifEmpty { null },
            dayOfMonth = day,
            category = category,
            account = account,
            creditCard = if (type.isIncome) null else creditCard,
            createdAt = createdAt,
        )
    }

    /**
     * Whether this form describes a template that could exist — the cheap reading the
     * UI needs to decide whether to offer the save.
     *
     * Read off [toRecurring] rather than restated: two hand-written copies of the same
     * rules is one of them drifting from the other, silently, in whichever direction
     * nobody is looking. The anchor is irrelevant here — a template is no more or less
     * valid for when it was created.
     */
    fun isValid(): Boolean = toRecurring(createdAt = 0L).isRight()

    companion object {
        fun from(
            type: TransactionType,
            amount: String,
            title: String,
            dayOfMonth: String,
            category: Category?,
            target: TransactionTarget,
            account: Account?,
            creditCard: CreditCard?,
        ): RecurringForm {
            
            val target = target.takeIf { type.isExpense } ?: TransactionTarget.ACCOUNT

            return RecurringForm(
                type = type,
                amount = amount,
                title = title,
                dayOfMonth = dayOfMonth,
                account = account?.takeIf { target.isAccount },
                creditCard = creditCard?.takeIf { target.isCreditCard },
                category = category?.takeIf { it.type.isAccept(type) },
            )
        }
    }
}
