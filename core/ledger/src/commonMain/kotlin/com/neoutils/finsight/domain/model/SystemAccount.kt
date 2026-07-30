package com.neoutils.finsight.domain.model

/**
 * Well-known names of the accounts the app creates for itself rather than for the
 * user: the `EQUITY` counterpart of every adjustment and write-off, the two nominal
 * accounts every income and expense lands on, where the residue of a currency
 * crossing lands, and the stand-ins for accounts deleted before closure existed.
 * Mirrored by the ledger migration SQL in `:core:database`; keep both in sync.
 *
 * **The identifying triple is `(type, name, currency)`, not `(type, name)`.** Every
 * row of the chart carries exactly one currency, system rows included — that is
 * what makes `Account.currency` mean the same thing on every line, which is in turn
 * what lets "a currency is fixed at creation" be a rule of the chart rather than a
 * rule of the account facade. So an expense in USD lands on `EXPENSES/USD`, and the
 * BRL residue of a crossing on `CONVERSION/BRL`.
 *
 * And the *nature* stopped being a key: `EQUITY` now names two system accounts per
 * currency (reconciliation and, under its own type, conversion), so resolution goes
 * by `(SystemAccount, currency)`.
 *
 * None of these is ever rendered. They are lookup keys, and the accounts they name
 * are invisible by construction: every listing and selector filters `type = 'ASSET'`,
 * which no row here is (design D10). What the user reads on a nominal leg is the
 * name of the *category* its dimension points at — or, with no dimension, the
 * "uncategorized" string resource.
 */
object SystemAccount {
    const val RECONCILIATION = "Reconciliação"

    /**
     * The two nominal accounts of the whole chart: every expense lands on one and
     * every income on the other, told apart by the dimension of the category the
     * leg carries. They replace the per-category accounts — a category is a
     * dimension now, not a row in the chart of accounts (design D4).
     */
    const val EXPENSES = "Despesas"
    const val INCOMES = "Receitas"

    /**
     * Where the residue of a transaction that crosses currencies lands, one account
     * **per currency** — `CONVERSION/BRL`, `CONVERSION/USD` — and not one per
     * *pair*. Created exclusively by the write boundary, on demand; never rendered
     * and never offered in a selector, like every other name here.
     *
     * Per currency is GnuCash's granularity (`Trading:CURRENCY:USD`), not hledger's
     * (`equity:conversion:$-€:$`). The cost is known and accepted: with three or
     * more currencies, `CONVERSION/BRL` accumulates the result of every crossing
     * against the real without distinguishing which pair produced it. It is a
     * **deliberate and hard-to-reverse** decision — going per pair later is a data
     * migration — accepted because exchange result by pair is a report this app does
     * not have and whose absence blocks nothing.
     */
    const val CONVERSION = "Conversão"

    /**
     * The reconstructed home of legs whose account or card was deleted back when
     * the app removed them instead of closing them. One per type: the real type
     * survives in the legacy `target`, the name and the multiplicity do not.
     */
    const val CLOSED_ACCOUNT = "Conta encerrada"
    const val CLOSED_CARD = "Cartão encerrado"
}
