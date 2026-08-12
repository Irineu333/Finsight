package com.neoutils.finsight.extension

import com.neoutils.finsight.domain.model.Category

/**
 * What a transaction, an installment or a recurring is called on screen: its own
 * title, or — when it has none — the name of its category.
 *
 * One owner, because the fallback is one rule. It used to live on `Transaction`,
 * which could state it because a transaction carried its category; once the ledger
 * stopped carrying facades (design D6) the rule was written out four times, in the
 * list mapper, the installment mapper, the transaction modal and `Recurring`. Four
 * copies of a fallback is how the empty-title case comes to read differently on
 * different screens.
 *
 * The `"Untitled"` literal is not localized, and was not before: it is the last
 * resort for a row with no title and no category, which the form makes hard to
 * reach (`BuildTransactionError.TitleOrCategoryRequired` demands one or the other).
 * Left as it was rather than quietly changed — it is a separate decision.
 */
fun displayTitleOf(title: String?, category: Category?): String =
    displayTitleOrNull(title, category) ?: "Untitled"

/**
 * The same fallback, stopping short of the last resort: `null` when the transaction
 * has neither a title of its own nor a category.
 *
 * A surface with room to say nothing — a detail header, whose first line already
 * states the nature — omits the line instead of giving a name to an absence. The
 * rule is one, and this is the half of it that is a fact about the transaction.
 */
fun displayTitleOrNull(title: String?, category: Category?): String? =
    title?.takeIf { it.isNotBlank() }
        ?: category?.name?.takeIf { it.isNotBlank() }
