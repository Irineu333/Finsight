package com.neoutils.finsight.extension

import com.neoutils.finsight.domain.model.Category

/**
 * What an operation is called by what it carries: its own title, or — when it has none —
 * the name of its category. `null` when it has neither.
 *
 * One owner, because it is one rule. It used to live on `Transaction`, which could state
 * it because a transaction carried its category; once the ledger stopped carrying facades
 * (design D6) the rule was written out four times, in the list mapper, the installment
 * mapper, the transaction modal and `Recurring`. Four copies of a fallback is how the
 * empty-title case comes to read differently on different screens.
 *
 * It stops here deliberately. What to say about an operation that has neither is not a
 * fact about the operation but about the surface: a list names it by its form, a detail
 * header that already announced the nature omits the line, and a model whose form is the
 * single owner of "title or category" asserts the invariant instead of naming an absence.
 * Only the surface knows which of those it is — and only the surface can resolve a
 * localized string.
 */
fun displayTitleOrNull(title: String?, category: Category?): String? =
    title?.takeIf { it.isNotBlank() }
        ?: category?.name?.takeIf { it.isNotBlank() }
