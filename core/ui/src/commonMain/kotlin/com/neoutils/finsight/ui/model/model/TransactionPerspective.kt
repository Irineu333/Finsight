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

/**
 * The leg whose amount **denominates the figure** — which is a different question from
 * [legUnder], and only for an operation whose two ends disagree on currency.
 *
 * With nobody in particular looking, an operation that crossed currencies has two equally
 * true figures: US$ 550,00 left and R$ 500,00 of an invoice was paid. Reading the outgoing
 * leg made the choice by accident — it is the leg [legUnder] answers because *direction*
 * has to come from somewhere — and a card payment then announced itself in a currency the
 * user does not keep accounts in. When one end is already in [baseCurrency], that end is
 * the one to state: it is the currency he thinks in, and it costs nothing, because the
 * figure is the ledger's own and no rate touches it.
 *
 * **Nothing is converted, and the base is never a fallback.** Where neither end is in the
 * base — a dollar account paying a real card under a euro base — the reading stays what it
 * was. Converting there would buy a currency nobody asked for at the price of a rate that
 * may not exist (`money-display`: the base MUST NOT be a resort for a figure whose own
 * currency is knowable). The base only ever picks between two exact figures.
 *
 * **It is not the leg the transaction is *read* through.** Direction and sign stay with
 * [legUnder], or an invoice payment would announce itself as income the moment the figure
 * moved to the liability end. That the two can differ at all is confined to the two labels
 * that render as a magnitude with no perspective — `TRANSFER` and `PAYMENT` — because a
 * single monetary leg has no second end to prefer.
 */
fun Transaction.figureLegUnder(accountId: Long?, baseCurrency: String?): Entry? {
    val read = legUnder(accountId) ?: return null

    // A surface that named an account is not asking this question: its figure is that
    // account's line, in that account's currency, whatever the base (design D29).
    if (accountId != null || baseCurrency == null) return read

    val monetary = monetaryEntries
    if (monetary.map { it.currency }.distinct().size < 2) return read

    return monetary.singleOrNull { it.currency == baseCurrency } ?: read
}
