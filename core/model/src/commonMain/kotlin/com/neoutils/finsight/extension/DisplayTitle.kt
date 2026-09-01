package com.neoutils.finsight.extension

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.transaction_card_balance_adjustment
import com.neoutils.finsight.resources.transaction_card_expense
import com.neoutils.finsight.resources.transaction_card_income
import com.neoutils.finsight.resources.transaction_card_invoice_adjustment
import com.neoutils.finsight.resources.transaction_card_payment
import com.neoutils.finsight.resources.transaction_card_transfer
import com.neoutils.finsight.resources.view_transaction_title_transfer
import com.neoutils.finsight.util.UiText
import org.jetbrains.compose.resources.StringResource

/**
 * What an operation is called by what it carries: its own title, or — when it has none —
 * the name of its category. `null` when it has neither.
 *
 * The first two links of the chain [operationName] completes, published on their own
 * because a flat DTO carries exactly this much: a list item's model answers "title or
 * category" and leaves the form to whichever surface renders it, which is the one link
 * that needs a localized string.
 */
fun displayTitleOrNull(title: String?, category: Category?): String? =
    title?.takeIf { it.isNotBlank() }
        ?: category?.name?.takeIf { it.isNotBlank() }

/**
 * The name of an operation, whole: its title, then its category, then its **form**.
 *
 * The third link is a total function of the nature — which is itself derived from the
 * account types of the entries — so every operation has a name and none falls back to a
 * generic literal. An adjustment is named by its target because that is the fact its
 * nature withholds, and the target is the ledger's own: the leg posts to a liability or
 * it does not.
 *
 * One owner, because it is one rule. It was written three times — the list item, the
 * exported document and the detail header — and three copies of "what is this operation
 * called" is how the same transaction comes to be named two ways in two places, which an
 * exported report sitting beside the screen it came from makes plain.
 *
 * @param displayTitle the first two links, already answered ([displayTitleOrNull]).
 */
fun operationName(
    displayTitle: String?,
    label: TransactionLabel,
    isCardTarget: Boolean,
): UiText = displayTitle?.let(UiText::Raw) ?: UiText.Res(label.formName(isCardTarget))

/**
 * The same chain, for a surface that has **already announced the nature** — a detail
 * header, whose two lines are read as one sentence.
 *
 * It differs from [operationName] in one register and not in one table: the form still
 * names the operation, but it says what the line above it did not. A transfer reads
 * "entre contas" beside a header that already said "transferência"; an expense and an
 * income have nothing left to add, and `null` is the surface omitting the line rather
 * than repeating itself.
 *
 * @param displayTitle the first two links, already answered ([displayTitleOrNull]).
 */
fun operationNameBesideNature(
    displayTitle: String?,
    label: TransactionLabel,
    isCardTarget: Boolean,
): UiText? = displayTitle?.let(UiText::Raw)
    ?: label.formComplement(isCardTarget)?.let(UiText::Res)

/**
 * Every cell of the form table, for a surface that resolves its strings **before** it
 * runs — the exported document is built outside the `@Composable` world and cannot ask
 * for one on demand.
 *
 * Derived from the table rather than listed beside it, so a nature added tomorrow is
 * resolvable by construction instead of by remembering to add it here too.
 */
fun operationFormNames(): Set<StringResource> = TransactionLabel.entries
    .flatMap { label -> listOf(label.formName(isCardTarget = false), label.formName(isCardTarget = true)) }
    .toSet()

/** What the operation *is*, said on its own. Total over the five natures. */
private fun TransactionLabel.formName(isCardTarget: Boolean): StringResource = when (this) {
    TransactionLabel.EXPENSE -> Res.string.transaction_card_expense
    TransactionLabel.INCOME -> Res.string.transaction_card_income
    TransactionLabel.TRANSFER -> Res.string.transaction_card_transfer
    TransactionLabel.PAYMENT -> Res.string.transaction_card_payment
    TransactionLabel.ADJUSTMENT -> if (isCardTarget) {
        Res.string.transaction_card_invoice_adjustment
    } else {
        Res.string.transaction_card_balance_adjustment
    }
}

/**
 * The same fact beside a nature already stated: `null` where the form has nothing the
 * nature did not already say, and the standalone name wherever it has.
 */
private fun TransactionLabel.formComplement(isCardTarget: Boolean): StringResource? = when (this) {
    // "despesa / despesa" and "receita / receita" spend a line without informing.
    TransactionLabel.EXPENSE, TransactionLabel.INCOME -> null
    // Named on its own it is "transferência"; beside that word it is where it went.
    TransactionLabel.TRANSFER -> Res.string.view_transaction_title_transfer
    TransactionLabel.PAYMENT, TransactionLabel.ADJUSTMENT -> formName(isCardTarget)
}
