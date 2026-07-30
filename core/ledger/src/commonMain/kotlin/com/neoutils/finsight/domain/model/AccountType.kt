package com.neoutils.finsight.domain.model

/**
 * The accounting nature of an [Account] in the unified chart of accounts.
 *
 * Debit-natured accounts ([ASSET], [EXPENSE]) increase with a positive (debit)
 * amount; credit-natured accounts ([LIABILITY], [INCOME], [EQUITY], [CONVERSION])
 * increase with a negative (credit) amount. The set is closed: no other account
 * type exists.
 */
enum class AccountType {
    ASSET,
    LIABILITY,
    INCOME,
    EXPENSE,
    EQUITY,

    /**
     * Where the residue of a transaction that crosses currencies lands, one account
     * per currency, created by the write boundary and postable by nothing else.
     *
     * It is **not** `EQUITY`, and that is the most expensive decision of the
     * multi-currency change. In this codebase an `EQUITY` leg already means exactly
     * one thing — "the user reconciled something by hand" — and four independent
     * places read it that way: the label derivation, the leg-direction derivation,
     * six aggregations of the entry queries, and the idempotence of the two balance
     * adjustments. Under `EQUITY` every cross-currency transfer would read as an
     * adjustment, and the adjustment of the day would be rewritten by a transfer.
     *
     * With a type of its own all four keep working untouched: the label falls
     * through (`{ASSET, CONVERSION}` → `TRANSFER`, `{ASSET, LIABILITY, CONVERSION}`
     * → `PAYMENT`), the `EQUITY` predicate still means only adjustment, and the
     * idempotence stops matching. The precedent agrees: GnuCash keeps
     * `ACCT_TYPE_TRADING` distinct from `ACCT_TYPE_EQUITY`, and hledger needed a
     * code of its own for the same reason.
     */
    CONVERSION;

    val isDebitNatured: Boolean get() = this == ASSET || this == EXPENSE
    val isCreditNatured: Boolean get() = !isDebitNatured

    /**
     * True for the account types that hold money: [ASSET] and [LIABILITY]. These
     * are where a balance physically *is*, and they are what the user chooses in
     * the form (an account or a card). The remaining types ([INCOME], [EXPENSE],
     * [EQUITY], [CONVERSION]) are the synthesized counterparty legs that explain
     * *why* money moved. Orthogonal to [isDebitNatured], which splits the same
     * types by their debit/credit direction rather than by whether they carry a
     * balance.
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
     *
     * [CONVERSION] is permanent **vacuously**: its balance is the realized exchange
     * result, which does carry across periods, and no screen ever archives it — so
     * the question this property answers cannot arise for it.
     */
    val isPermanent: Boolean
        get() = this == ASSET || this == LIABILITY || this == EQUITY || this == CONVERSION

    /**
     * The complement of [isPermanent] restricted to the two types money *flows*
     * through: `INCOME` and `EXPENSE`. These are the only accounts a category
     * dimension may land on, which makes this the way to find the leg that carries
     * one.
     */
    val isNominal: Boolean get() = this == INCOME || this == EXPENSE
}
