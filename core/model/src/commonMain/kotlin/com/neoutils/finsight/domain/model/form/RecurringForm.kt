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
     * The direction settles what the template may hold, and what it cannot hold is
     * dropped rather than carried: a card takes expenses only, and a category
     * classifies one direction only, so an income keeps neither the card nor an
     * expense category. A caller whose values were declared rather than chosen from a
     * selector — one that cannot re-offer them — checks the same rule before it gets
     * here, because to it the drop is indistinguishable from an acceptance.
     *
     * [createdAt] is the anchor the cycle numbering is counted from, and it is the
     * caller's to decide: the clock, for a template created from the recurring form;
     * the transaction's own date, for one born out of a transaction.
     */
    fun toRecurring(createdAt: Long): Either<RecurringError, Recurring> = either {
        val category = category?.takeIf { it.type.isAccept(type) }

        ensure(amount.isNotEmpty()) { RecurringError.AMOUNT_REQUIRED }
        ensure(amount.moneyToDouble() > 0.0) { RecurringError.AMOUNT_NOT_POSITIVE }
        // Blank, not empty: whitespace is not a name, and the surfaces that read the
        // template's label say so too. Accepting it here would let a row through that
        // satisfies the owner of the rule and nothing that reads it.
        ensure(title.isNotBlank() || category != null) {
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
            title = title.trim().ifEmpty { null },
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
        /**
         * The form as a screen holds it: of the two destinations it keeps the one
         * [target] points at, and an income points at an account whatever the selector
         * was left on, because that is the only place an income posts.
         *
         * Only the destination is settled here. Whether the direction can carry the rest
         * of what the form holds is [toRecurring]'s, and every caller reaches it —
         * including the ones that never pass through this.
         */
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
                category = category,
            )
        }
    }
}
