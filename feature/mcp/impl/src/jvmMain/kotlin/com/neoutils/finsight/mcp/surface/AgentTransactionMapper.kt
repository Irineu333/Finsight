package com.neoutils.finsight.mcp.surface

import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.matches
import com.neoutils.finsight.extension.deriveTransactionLabel
import com.neoutils.finsight.extension.deriveTransactionType
import com.neoutils.finsight.extension.displayTitleOf
import com.neoutils.finsight.ui.model.TransactionFacadeLookup
import com.neoutils.finsight.ui.model.TransactionPerspective
import com.neoutils.finsight.ui.model.figureLegUnder
import com.neoutils.finsight.ui.model.itemDisplayAmount
import com.neoutils.finsight.ui.model.legUnder

/**
 * Maps a [Transaction] to the flat [AgentTransaction] a tool answers with — the sibling of
 * `Transaction.toTransactionUi`, and deliberately so.
 *
 * **Two models, one set of decisions.** The screen's model carries an icon and a theme colour that
 * mean nothing to an agent, and this one carries account and category names that a screen already
 * shows in its header. What neither may do is decide anything for itself: the nature comes from
 * `deriveTransactionLabel`, the leg to read from [legUnder] under the caller's
 * [TransactionPerspective], the end that denominates a cross-currency figure from [figureLegUnder],
 * and the sign from [itemDisplayAmount]. Every one of those already has an owner, and a second
 * derivation of the same choice can disagree with the first without anything failing — which
 * between a screen and an agent is the hardest kind of divergence to notice, because nobody ever
 * sees the two side by side.
 *
 * Returns `null` when the perspective has no leg here, so a caller drops the item instead of
 * failing on a read — the same contract the screen's mapper keeps.
 *
 * @param perspective the account (a card enters through `CreditCard.accountId`) this transaction is
 * being read under, or `null` for a listing with no point of view.
 * @param lookup what closes the gap the ledger leaves: a transaction carries the *dimension* its
 * nominal leg is classified by and the *id* of its instalment, and turning either into something
 * with a name belongs to whoever owns that facade.
 * @param baseCurrency what a surface with **no** perspective answers with when the two ends of an
 * operation disagree on currency. Nothing is converted by it — it only picks between two exact
 * figures, and only when one of them is already in it.
 */
internal fun Transaction.toAgentTransaction(
    perspective: TransactionPerspective? = null,
    lookup: TransactionFacadeLookup = TransactionFacadeLookup.EMPTY,
    baseCurrency: String? = null,
): AgentTransaction? {
    val accountId = perspective?.accountId
    val category = lookup.categoryOf(this)
    val label = entries.deriveTransactionLabel()

    val leg = legUnder(accountId) ?: return null
    // Two legs, deliberately: direction is the leg this transaction is *read* through, while the
    // figure may sit at the other end when the two disagree on currency and one of them is the
    // base. They coincide everywhere else.
    val figure = figureLegUnder(accountId, baseCurrency) ?: leg

    val display = itemDisplayAmount(
        label = label,
        legAmountCents = figure.amount,
        currency = figure.currency,
        hasPerspective = accountId != null,
    )

    return AgentTransaction(
        id = id,
        nature = label.wireName,
        // Withheld without a perspective on purpose: there is no account to see the movement from,
        // and answering with the direction of whichever leg was read would state as a property of
        // the transaction something that is only true of one end of it.
        direction = accountId?.let { deriveTransactionType(leg.amount, entries).wireName },
        title = displayTitleOf(title, category),
        // A line of a statement is a single leg, so the figure is exact and stands in that leg's
        // own currency — never consolidated, and never the base by default.
        amount = AgentFigure.exact(display.value, display.currency),
        date = date,
        account = leg.account.name,
        accountId = leg.account.id,
        category = category?.name,
        categoryId = category?.id,
        // Decided by the single owner of what a value of the analytic axis contains, so the surface
        // that answers an agent cuts by the same rule as the one that cuts the ledger.
        isUncategorized = matches(SpendingSubject.Uncategorized),
        isOnCard = hasLiabilityLeg,
        isRecurring = recurringId != null,
        installment = lookup.installmentLabelOf(this),
    )
}

/**
 * The wire spelling of a derived vocabulary: the constant, lowercased.
 *
 * The enum stays out of the payload — JSON has no enums, and a domain type crossing this boundary
 * is what `presentation-mapping` forbids — but the *values* are the ledger's own, so an agent that
 * reads `transfer` is reading `TransactionLabel.TRANSFER` and nothing else.
 */
private val TransactionLabel.wireName: String get() = name.lowercase()

private val TransactionType.wireName: String get() = name.lowercase()
