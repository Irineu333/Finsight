package com.neoutils.finsight.domain.model

/**
 * A single leg of a balanced [Transaction].
 *
 * [amount] is signed and expressed in the currency's minor unit (e.g. cents),
 * following the debit-positive convention: a positive amount debits the account,
 * a negative amount credits it. For every currency present in an transaction, the
 * sum of its entries' amounts is exactly zero.
 */
data class Entry(
    val id: Long = 0,
    val transactionId: Long? = null,
    val account: Account,
    val amount: Long,
    val currency: String = BASE_CURRENCY,
    // Set only on the credit-card (LIABILITY) leg of a purchase: the invoice this
    // leg belongs to. Makes the entry a complete leg, so an invoice's balance is
    // Σ entries carrying its id — the sub-ledger of the card account.
    val invoiceId: Long? = null,
)
