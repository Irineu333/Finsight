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
     * Where the residue of a transaction that crosses currencies lands, one
     * account per currency, created on demand by the write boundary and postable
     * by nothing else.
     *
     * **Why it is not [EQUITY].** In this code `EQUITY` already means one thing —
     * "the user reconciled something by hand" — and four behaviours read it that
     * way. Putting conversion there breaks all four, none of them silently
     * fixable:
     *
     * - `deriveTransactionLabel` tests `EQUITY` *before any other case*, so every
     *   cross-currency transfer would read `ADJUSTMENT` instead of `TRANSFER`;
     * - `deriveTransactionType` repeats the predicate, so each monetary leg of a
     *   cross-currency operation would read "adjustment";
     * - the six `EXISTS(... a.type = 'EQUITY') AS eq` predicates of `EntryDao`
     *   would classify a cross-currency card payment as an adjustment, letting it
     *   into month flows that exclude transfers and payments;
     * - the idempotency of `AdjustBalanceUseCase` ("the adjustment of that date on
     *   that account is the transaction with an `EQUITY` leg") would mistake a
     *   same-day cross-currency transfer for the adjustment and **rewrite it**.
     *
     * With a type of its own the four resolve themselves, untouched: the label
     * falls through (`{ASSET, CONVERSION}` → `TRANSFER`), the `eq` predicate keeps
     * meaning only "adjustment", and the idempotency stops matching. It is what
     * GnuCash does (`ACCT_TYPE_TRADING`, distinct from `ACCT_TYPE_EQUITY`) and
     * what hledger needed even under an `equity:` namespace.
     */
    CONVERSION;

    val isDebitNatured: Boolean get() = this == ASSET || this == EXPENSE
    val isCreditNatured: Boolean get() = !isDebitNatured

    /**
     * True for the account types that hold money: [ASSET] and [LIABILITY]. These
     * are where a balance physically *is*, and they are what the user chooses in
     * the form (an account or a card). The remaining types ([INCOME], [EXPENSE],
     * [EQUITY], [CONVERSION]) are the synthesized counterparty legs that explain
     * *why* money moved. Orthogonal to [isDebitNatured], which splits the same six
     * types by their debit/credit direction rather than by whether they carry a
     * balance.
     *
     * [CONVERSION] is deliberately outside: it is not where money is, it is not
     * offered in any form, and keeping it out is what makes the editability gate
     * ("exactly one monetary leg") keep refusing a cross-currency operation
     * without a rule of its own.
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
     * [CONVERSION] is permanent **vacuously**: its balance does carry across
     * periods (it is the realized exchange result), but the property exists to
     * decide whether archiving could strand a balance, and a conversion account is
     * never archived — no screen offers it and no selector shows it.
     */
    val isPermanent: Boolean get() =
        this == ASSET || this == LIABILITY || this == EQUITY || this == CONVERSION

    /**
     * The complement of [isPermanent] restricted to the two types money *flows*
     * through: `INCOME` and `EXPENSE`. These are the only accounts a category
     * dimension may land on, which makes this the way to find the leg that carries
     * one. [CONVERSION] carries no dimension at all (design D15).
     */
    val isNominal: Boolean get() = this == INCOME || this == EXPENSE
}
