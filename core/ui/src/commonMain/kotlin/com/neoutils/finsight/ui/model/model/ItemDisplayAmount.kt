package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.extension.DisplayAmount

/**
 * The item surface's sign rule, in one place: how the leg of a transaction reads on a
 * card, in its detail modal and on its line of the exported report.
 *
 * A sign is only shown where the label does not already give the direction. "Gasto",
 * "Receita" and "Pagamento" do, in any perspective, so they read as magnitudes.
 *
 * An **adjustment** always shows one: it is the only transaction whose direction the
 * label withholds. The sign is the ledger's own — debit-positive, exactly as the leg was
 * recorded — never inverted by `AccountType`, which is a rule about *balances*. So a
 * debt that grows reads negative, next to the purchase that grows the same debt.
 *
 * A **transfer** shows one at both ends when read under a perspective, because the two
 * legs share label, icon and color and nothing else tells them apart; signing only one
 * would make the reader infer the other from absence. Read without a perspective there
 * is no direction to show — the same transaction holds both ends.
 *
 * There are two producers of this surface ([Transaction.toTransactionUi] for the lists
 * and the report, the detail modal's state for the modal) and one table; each resolving
 * it on its own is how the two would drift apart.
 */
/**
 * @param currency the currency of the **leg's own account**. A line of a statement is a
 * single entry, so it is exact and never the base currency by default — an item of a
 * dollar account reads in dollars whatever the user's base is (design D29).
 */
fun itemDisplayAmount(
    label: TransactionLabel,
    legAmountCents: Long,
    currency: String,
    hasPerspective: Boolean,
): DisplayAmount {
    val value = legAmountCents / 100.0

    fun explicitSign() = DisplayAmount.explicitSign(value, currency, isApproximate = false)
    fun magnitude() = DisplayAmount.magnitude(value, currency, isApproximate = false)

    return when (label) {
        TransactionLabel.ADJUSTMENT -> explicitSign()
        TransactionLabel.TRANSFER -> if (hasPerspective) explicitSign() else magnitude()

        TransactionLabel.EXPENSE,
        TransactionLabel.INCOME,
        TransactionLabel.PAYMENT -> magnitude()
    }
}
