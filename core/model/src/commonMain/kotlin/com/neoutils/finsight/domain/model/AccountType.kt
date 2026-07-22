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

    /**
     * A *permanent* (real) account, in the accounting sense: its balance is what
     * exists right now and carries across periods. `INCOME` and `EXPENSE` are
     * *temporary* (nominal) — their balance is a period total, zeroed only by a
     * period-closing entry into equity, which this app does not perform.
     *
     * The distinction is what decides whether a balance can be *stranded*: money
     * sits in a permanent account, so archiving one that still holds some would
     * leave it in net worth with nothing visible to explain it. A temporary
     * account holds nothing — its balance is a total of things that already moved.
     */
    val isPermanent: Boolean get() = this == ASSET || this == LIABILITY || this == EQUITY

    /**
     * The complement of [isPermanent] restricted to the two types money *flows*
     * through: `INCOME` and `EXPENSE`. These are the only accounts a category
     * dimension may land on, which makes this the way to find the leg that carries
     * one.
     */
    val isNominal: Boolean get() = this == INCOME || this == EXPENSE
}
