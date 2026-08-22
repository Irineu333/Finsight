package com.neoutils.finsight.domain.model

import com.neoutils.finsight.extension.displayTitleOrNull

data class Recurring(
    val id: Long = 0,
    val type: TransactionType,
    val amount: Double,
    val title: String?,
    val dayOfMonth: Int,
    val category: Category?,
    val account: Account?,
    val creditCard: CreditCard?,
    val createdAt: Long,
    val isArchived: Boolean = false,
) {
    /**
     * What the template is called: its own title, or the name of its category.
     *
     * There is no third link to invent, and none is needed: `RecurringForm.toRecurring`
     * is the single owner of "a template has a title or a category", every write goes
     * through it, and archiving only copies a flag onto a row that already passed. So the
     * absence is asserted rather than papered over with a name the user never chose — the
     * day a new write path skips that owner, this is what says so, instead of a screen
     * quietly reading wrong.
     */
    val label
        get() = displayTitleOrNull(title, category)
            ?: error("A recurring has a title or a category (RecurringForm.toRecurring).")

    /**
     * Whether the money still has somewhere to move through.
     *
     * False when the account or card was deleted (the reference is gone) or
     * archived (it exists, but receives nothing new). The template survives
     * either way — it just cannot be posted until the user points it somewhere
     * real, which is why this is derived and never persisted.
     *
     * [category] is deliberately not part of it: "uncategorized" is a legitimate
     * ledger state, backed by a system account. An accountless transaction is not.
     */
    val hasUsableSource: Boolean
        get() = creditCard?.isArchived == false || account?.isArchived == false
}
