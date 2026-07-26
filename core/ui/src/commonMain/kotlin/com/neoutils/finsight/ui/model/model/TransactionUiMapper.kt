package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.deriveTransactionLabel
import com.neoutils.finsight.extension.displayTitleOf
import com.neoutils.finsight.extension.deriveTransactionType

/**
 * Maps an [Transaction] to its flat [TransactionUi], deriving both display axes from
 * the ledger entries — the single domain→presentation boundary for a list item.
 *
 * The perspective leg is the entry the screen looks through: the entry in
 * [accountId] when a perspective is given, otherwise [Transaction.primaryEntry],
 * which is where that choice is defined — the mapper consumes it instead of
 * restating the criterion, so a list without perspective and the detail can never
 * disagree about which leg they read. Returns `null` when the perspective has no
 * matching leg, so the caller omits the item instead of failing on a read.
 *
 * [lookup] closes the gap the ledger leaves: a transaction carries the *dimension*
 * its nominal leg is classified by and the *id* of its installment, and turning
 * either into something with a name belongs to the feature that owns that facade
 * (design D6). Left empty, the item simply renders without them.
 */
fun Transaction.toTransactionUi(
    accountId: Long? = null,
    lookup: TransactionFacadeLookup = TransactionFacadeLookup.EMPTY,
): TransactionUi? {
    val category = lookup.categoryOf(this)
    val label = entries.deriveTransactionLabel()

    val leg = if (accountId != null) {
        entries.firstOrNull { it.account.id == accountId }
    } else {
        primaryEntry
    } ?: return null

    return TransactionUi(
        id = id,
        label = label,
        direction = deriveTransactionType(leg.amount, entries),
        title = displayTitleOf(title, category),
        amount = itemDisplayAmount(label, leg.amount, hasPerspective = accountId != null),
        date = date,
        categoryId = category?.id,
        categoryIcon = category?.icon,
        isCategoryArchived = category?.isArchived == true,
        isCardTarget = entries.any { it.account.type == AccountType.LIABILITY },
        isRecurring = recurringId != null,
        installmentLabel = lookup.installmentLabelOf(this),
    )
}
