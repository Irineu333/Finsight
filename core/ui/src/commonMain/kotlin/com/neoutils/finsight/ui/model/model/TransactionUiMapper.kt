package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.deriveTransactionLabel
import com.neoutils.finsight.extension.deriveTransactionType
import kotlin.math.abs

/**
 * Maps an [Transaction] to its flat [TransactionUi], deriving both display axes from
 * the ledger entries — the single domain→presentation boundary for a list item.
 *
 * The perspective leg is the entry the screen looks through: the entry in
 * [accountId] when a perspective is given, otherwise the transaction's own money
 * leg (the outgoing one for a two-leg transaction, which is how a transfer or a
 * payment reads from a neutral list). Returns `null` when the perspective has no
 * matching leg, so the caller omits the item instead of failing on a read.
 */
fun Transaction.toTransactionUi(accountId: Long? = null): TransactionUi? {
    val leg = if (accountId != null) {
        entries.firstOrNull { it.account.id == accountId }
    } else {
        entries.filter { it.account.type.isMonetary }.minByOrNull { it.amount }
    } ?: return null

    return TransactionUi(
        id = id,
        label = entries.deriveTransactionLabel(),
        direction = deriveTransactionType(leg.amount, entries),
        title = displayTitle,
        amount = abs(leg.amount) / 100.0,
        date = date,
        categoryId = category?.id,
        categoryIcon = category?.icon,
        isCategoryArchived = category?.isArchived == true,
        isCardTarget = entries.any { it.account.type == AccountType.LIABILITY },
        isRecurring = recurring != null,
        installmentLabel = installment?.label,
    )
}
