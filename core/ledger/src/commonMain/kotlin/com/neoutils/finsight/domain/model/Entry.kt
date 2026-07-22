package com.neoutils.finsight.domain.model

/**
 * A single leg of a balanced [Transaction].
 *
 * [amount] is signed and expressed in the currency's minor unit (e.g. cents),
 * following the debit-positive convention: a positive amount debits the account,
 * a negative amount credits it. For every currency present in a transaction, the
 * sum of its entries' amounts is exactly zero.
 */
data class Entry(
    val id: Long = 0,
    val transactionId: Long? = null,
    val account: Account,
    val amount: Long,
    val currency: String = BASE_CURRENCY,
    // The analytic axis this leg is tagged with, if any — the sub-ledger it belongs
    // to inside its account. A facade's total is Σ entries carrying its dimension.
    val dimensionId: Long? = null,
)
