package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.matches
import com.neoutils.finsight.extension.deriveTransactionLabel
import com.neoutils.finsight.extension.displayTitleOrNull
import com.neoutils.finsight.extension.deriveTransactionType

/**
 * Maps an [Transaction] to its flat [TransactionUi], deriving both display axes from
 * the ledger entries — the single domain→presentation boundary for a list item.
 *
 * Which leg is read is [Transaction.legUnder]'s to say, not the mapper's — the same
 * definition the detail and the list's own filter consume, so the three cannot disagree
 * about the leg they are all supposedly showing. Returns `null` when the perspective has
 * no matching leg, so the caller omits the item instead of failing on a read.
 *
 * [baseCurrency] is what a surface with **no perspective** answers with when the two ends
 * of an operation disagree on currency — see [Transaction.figureLegUnder]. A surface that
 * does not know it, or that named an account, reads exactly what it read before.
 *
 * [lookup] closes the gap the ledger leaves: a transaction carries the *dimension*
 * its nominal leg is classified by and the *id* of its installment, and turning
 * either into something with a name belongs to the feature that owns that facade
 * (design D6). Left empty, the item simply renders without them.
 */
fun Transaction.toTransactionUi(
    accountId: Long? = null,
    lookup: TransactionFacadeLookup = TransactionFacadeLookup.EMPTY,
    baseCurrency: String? = null,
): TransactionUi? {
    val category = lookup.categoryOf(this)
    val label = entries.deriveTransactionLabel()

    val leg = legUnder(accountId) ?: return null
    // Two legs, deliberately: direction is the leg this transaction is *read* through,
    // while the figure may sit at the other end when the two disagree on currency and
    // one of them is the base ([figureLegUnder]). They coincide everywhere else.
    val figure = figureLegUnder(accountId, baseCurrency) ?: leg

    return TransactionUi(
        id = id,
        label = label,
        direction = deriveTransactionType(leg.amount, entries),
        title = displayTitleOrNull(title, category),
        amount = itemDisplayAmount(
            label = label,
            legAmountCents = figure.amount,
            currency = figure.currency,
            hasPerspective = accountId != null,
        ),
        date = date,
        categoryId = category?.id,
        // Decided by the single owner of what a value of the analytic axis contains, so a
        // surface that cuts display models cuts by the same rule as one that cuts the ledger.
        isUncategorized = matches(SpendingSubject.Uncategorized),
        categoryIcon = category?.icon,
        isCategoryArchived = category?.isArchived == true,
        isCardTarget = entries.any { it.account.type == AccountType.LIABILITY },
        isRecurring = recurringId != null,
        installmentLabel = lookup.installmentLabelOf(this),
    )
}
