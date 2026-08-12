package com.neoutils.finsight.ui.model

import androidx.compose.runtime.Immutable
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionInstallment
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.liabilityLeg
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.view_transaction_leg_verb_adjusted
import com.neoutils.finsight.resources.view_transaction_leg_verb_charged
import com.neoutils.finsight.resources.view_transaction_leg_verb_entered
import com.neoutils.finsight.resources.view_transaction_leg_verb_left
import com.neoutils.finsight.resources.view_transaction_leg_verb_settled
import com.neoutils.finsight.util.UiText
import kotlinx.datetime.YearMonth

/**
 * One monetary leg of a transaction, ready to be rendered: which account or card,
 * how much, and what happened to that money.
 *
 * Flat on purpose — no domain graph, no leg, no account. Everything a card branches
 * on is resolved here by [toTransactionLegs]: the [verb] as text, the sign policy
 * inside [amount], the order of the list. A component renders it and derives
 * nothing.
 */
@Immutable
data class TransactionLegUi(
    /** What happened to this money, derived from the ledger — see [toTransactionLegs]. */
    val verb: UiText,
    /** The account's or the card's own name — a card's `LIABILITY` account mirrors it. */
    val name: String,
    /** Stated only where the operation touches two currencies and they must be told apart. */
    val currencyCode: String?,
    val amount: DisplayAmount,
    /** The dimension a liability leg carries, when the transaction has an invoice. */
    val invoice: LegInvoiceUi? = null,
    val installment: LegInstallmentUi? = null,
    /** Absent when the facade is archived: the screen it would open no longer lists it. */
    val onClick: (() -> Unit)? = null,
)

/**
 * The invoice a liability leg belongs to, as the two facts a card states about it.
 * The label and the colour are rendered from these by the one owner of each
 * (`Invoice.toLabel`, `Invoice.Status.color`).
 */
@Immutable
data class LegInvoiceUi(
    val dueMonth: YearMonth,
    val status: Invoice.Status,
)

/**
 * The instalment a leg belongs to: which one of how many, and the arrangement's
 * total denominated by the card the instalment sits on.
 */
@Immutable
data class LegInstallmentUi(
    val label: String,
    val total: DisplayAmount,
)

/**
 * Which leg of an operation, and which end of it, the caller may open.
 *
 * Flat identity rather than a lambda per leg: the navigation itself is the caller's
 * — `:core:ui` names no feature route — but *whether* there is one to offer is this
 * mapper's, and it answers no for an archived facade.
 */
data class TransactionLegTarget(
    val accountId: Long,
    val isLiability: Boolean,
)

/**
 * Maps a transaction to one [TransactionLegUi] per **monetary** leg — the single
 * domain→presentation boundary of the operation surface.
 *
 * The criterion is the number of monetary legs and never the number of currencies:
 * a transfer between two accounts in the same currency states the same figure twice,
 * which is the assertion that nothing was lost on the way. The nominal legs — the
 * category, the reconciliation, the conversion residue — carry no money the user
 * recognises as his, and produce no card.
 *
 * **The verb is read off the ledger, never off [Transaction.label]**: the pair
 * `(account type, sign of the leg)`, with one override — a transaction with an
 * `EQUITY` leg is an adjustment, and all of its legs say so. Deriving it from the
 * same facts the nature is derived from is what keeps the two from ever disagreeing.
 *
 * **The figure is a magnitude**, because the verb already gives the direction, and
 * an adjustment is the exception because "adjusted" withholds it — the same
 * principle [itemDisplayAmount] applies to the item surface with the label as its
 * evidence. The adjustment's sign is the ledger's own, debit-positive, never
 * inverted by account type.
 *
 * The order is [Transaction.primaryEntry] first — the leg money left, consumed from
 * the one owner of that definition rather than restated here — so the arrow between
 * two cards and the direction the applied rate divides in agree by construction.
 *
 * @param invoice the invoice the liability leg carries, when the feature resolved one.
 * @param installment the instalment this transaction belongs to; its total is
 * denominated by the card, which is the one account an instalment names.
 * @param onOpen builds the shortcut of a leg. Never called for an archived facade.
 */
fun Transaction.toTransactionLegs(
    invoice: Invoice? = null,
    installment: TransactionInstallment? = null,
    onOpen: ((TransactionLegTarget) -> Unit)? = null,
): List<TransactionLegUi> {
    val legs = monetaryEntries
    if (legs.isEmpty()) return emptyList()

    val isAdjustment = entries.any { it.account.type == AccountType.EQUITY }
    // The same question the selectors ask, asked of what this surface shows: two
    // currencies on screen have to be told apart, one never does.
    val namesCurrency = legs.map { it.account.currency }.distinct().size > 1

    val installmentUi = installment?.let { arrangement ->
        entries.liabilityLeg()?.let { leg ->
            LegInstallmentUi(
                label = arrangement.label,
                total = DisplayAmount.magnitude(
                    value = arrangement.instance.totalAmount,
                    currency = leg.currency,
                    isApproximate = false,
                ),
            )
        }
    }

    val ordered = primaryEntry
        ?.let { primary -> listOf(primary) + legs.filter { it !== primary } }
        ?: legs

    return ordered.map { entry ->
        val isLiability = entry.account.type == AccountType.LIABILITY
        TransactionLegUi(
            verb = entry.verb(isAdjustment),
            name = entry.account.name,
            currencyCode = entry.account.currency.takeIf { namesCurrency },
            amount = entry.legAmount(isAdjustment),
            invoice = invoice?.takeIf { isLiability }
                ?.let { LegInvoiceUi(dueMonth = it.dueMonth, status = it.status) },
            installment = installmentUi?.takeIf { isLiability },
            onClick = onOpen
                ?.takeUnless { entry.account.isArchived }
                ?.let { open ->
                    {
                        open(
                            TransactionLegTarget(
                                accountId = entry.account.id,
                                isLiability = isLiability,
                            )
                        )
                    }
                },
        )
    }
}

private fun Entry.verb(isAdjustment: Boolean): UiText = UiText.Res(
    when {
        isAdjustment -> Res.string.view_transaction_leg_verb_adjusted
        account.type == AccountType.LIABILITY ->
            if (amount < 0) {
                Res.string.view_transaction_leg_verb_charged
            } else {
                Res.string.view_transaction_leg_verb_settled
            }

        else ->
            if (amount < 0) {
                Res.string.view_transaction_leg_verb_left
            } else {
                Res.string.view_transaction_leg_verb_entered
            }
    }
)

private fun Entry.legAmount(isAdjustment: Boolean): DisplayAmount {
    val value = amount / 100.0
    return if (isAdjustment) {
        DisplayAmount.explicitSign(value, currency, isApproximate = false)
    } else {
        DisplayAmount.magnitude(value, currency, isApproximate = false)
    }
}
