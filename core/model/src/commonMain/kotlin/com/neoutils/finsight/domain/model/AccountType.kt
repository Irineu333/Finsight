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

    /**
     * True for the account types that hold money: [ASSET] and [LIABILITY]. These
     * are where a balance physically *is*, and they are what the user chooses in
     * the form (an account or a card). The remaining types ([INCOME], [EXPENSE],
     * [EQUITY]) are the synthesized counterparty legs that explain *why* money
     * moved. Orthogonal to [isDebitNatured], which splits the same five types by
     * their debit/credit direction rather than by whether they carry a balance.
     */
    val isMonetary: Boolean get() = this == ASSET || this == LIABILITY
}
