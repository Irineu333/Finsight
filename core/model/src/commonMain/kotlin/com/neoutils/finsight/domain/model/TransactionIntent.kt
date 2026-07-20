package com.neoutils.finsight.domain.model

import kotlinx.datetime.LocalDate

/**
 * What the user asked for, before the ledger exists.
 *
 * This is the **input** side of the write boundary: the vocabulary the user
 * chose (a type, an amount, an account or a card, a category), not yet balanced
 * and not yet resolved to chart-of-accounts rows. `LedgerEntryWriter` is the
 * single translator from an intent to its balanced [Entry] set — resolving the
 * counterpart account has a side effect (it creates the row on demand), so the
 * translation cannot be a pure mapper.
 */
data class TransactionIntent(
    val title: String?,
    val date: LocalDate,
    val category: Category? = null,
    val recurringId: Long? = null,
    val recurringCycle: Int? = null,
    val installmentId: Long? = null,
    val installmentNumber: Int? = null,
    val legs: List<TransactionLeg>,
)

/**
 * One side of the user's intent. [type] and the account/card choice are the
 * retained *input* vocabulary (design D4): they are what the user picked, not a
 * property of the resulting ledger — `ASSET` vs `LIABILITY` determines the
 * target, and the entry signs determine the direction.
 */
data class TransactionLeg(
    val type: TransactionType,
    val amount: Double,
    val account: Account? = null,
    val creditCard: CreditCard? = null,
    val invoice: Invoice? = null,
    val category: Category? = null,
) {
    /** Derived from the choice: an account leg targets the account, else the card. */
    val target: TransactionTarget
        get() = if (account != null) TransactionTarget.ACCOUNT else TransactionTarget.CREDIT_CARD
}
