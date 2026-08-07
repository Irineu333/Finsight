package com.neoutils.finsight.domain.model.form

import com.neoutils.finsight.domain.error.ClosedFacade
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.extension.isAccept
import kotlinx.datetime.YearMonth

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
    /**
     * The **monetary** legs this form points at that are archived, and so would be
     * refused by the write boundary (`LedgerError.ClosedAccount`).
     *
     * A form can only reach this state by being *seeded* from an existing
     * transaction: the selectors only ever list open items.
     *
     * An archived *category* is deliberately absent. It refuses nothing — a
     * category holds no money, so writing to a closed one strands none — and
     * keeping it out of a new transaction is the selector's job, not a rule that
     * should also freeze the edit of an old one.
     */
    val archivedSelections: Set<ClosedFacade>
        get() = buildSet {
            if (account?.isArchived == true) add(ClosedFacade.ACCOUNT)
            if (creditCard?.isArchived == true) add(ClosedFacade.CREDIT_CARD)
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
