package com.neoutils.finsight.domain.model

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
    val isActive: Boolean = true,
) {
    val label get() = title?.takeIf { it.isNotBlank() } ?: category?.name?.takeIf { it.isNotBlank() } ?: "Untitled"

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
