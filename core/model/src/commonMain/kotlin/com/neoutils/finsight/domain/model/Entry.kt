package com.neoutils.finsight.domain.model

/**
 * A single leg of a balanced [Operation].
 *
 * [amount] is signed and expressed in the currency's minor unit (e.g. cents),
 * following the debit-positive convention: a positive amount debits the account,
 * a negative amount credits it. For every currency present in an operation, the
 * sum of its entries' amounts is exactly zero.
 */
data class Entry(
    val id: Long = 0,
    val operationId: Long? = null,
    val account: Account,
    val amount: Long,
    val currency: String = BASE_CURRENCY,
)
