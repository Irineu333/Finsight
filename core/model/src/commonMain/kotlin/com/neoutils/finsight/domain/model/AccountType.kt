package com.neoutils.finsight.domain.model

/**
 * The accounting nature of an [Account] in the unified chart of accounts.
 *
 * Debit-natured accounts ([ASSET], [EXPENSE]) increase with a positive (debit)
 * amount; credit-natured accounts ([LIABILITY], [INCOME], [EQUITY]) increase with
 * a negative (credit) amount. The set is closed: no other account type exists.
 */
enum class AccountType {
    ASSET,
    LIABILITY,
    INCOME,
    EXPENSE,
    EQUITY;

    val isDebitNatured: Boolean get() = this == ASSET || this == EXPENSE
    val isCreditNatured: Boolean get() = !isDebitNatured
}
