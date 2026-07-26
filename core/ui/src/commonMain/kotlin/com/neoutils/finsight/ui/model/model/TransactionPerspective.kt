package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Transaction

/**
 * The point of view under which a transaction is presented: the account (the card
 * enters via `CreditCard.accountId`) whose leg the screen shows. A single data
 * class — the sealed `Account`/`Card` split existed only because the legacy leg
 * had two forms; an `Entry` has one.
 */
data class TransactionPerspective(
    val accountId: Long,
    val invoiceId: Long? = null,
)

/**
 * The leg a surface reads this transaction through: the entry in [accountId] when the
 * surface has a perspective, otherwise [Transaction.primaryEntry] — the transaction's own
 * outgoing leg, which is how it reads with nobody in particular looking.
 *
 * One owner, because a surface reads its own legs more than once: the item it renders, the
 * detail it opens, and the filter it applies must not each decide this for themselves. Two
 * definitions can disagree, and then a filter returns a transaction the item next to it
 * presents in the opposite direction.
 *
 * `null` when the perspective has no leg here, so the caller omits the item instead of
 * failing on a read.
 */
fun Transaction.legUnder(accountId: Long?): Entry? = when (accountId) {
    null -> primaryEntry
    else -> entries.firstOrNull { it.account.id == accountId }
}
