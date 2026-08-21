package com.neoutils.finsight.domain.model

/**
 * What registering a transaction form wrote — one transaction, or the N of an
 * instalment plan.
 *
 * The answer names the branch that ran because the caller would otherwise have to
 * re-derive it from the form, which is the very decision the register owns: a screen
 * reports the save it made and a surface answering a request from outside reports
 * what it created, and neither may reach that by re-reading `installments > 1`.
 *
 * [transactions] is what every caller needs and no caller has to unwrap for — the
 * transactions actually written, in the order they were written, never empty. A
 * template born with its first cycle is a [Single]: the transaction carries the
 * `recurringId` of the template it opened, so nothing about it is lost here.
 */
sealed interface TransactionRegistration {

    /** The transactions written, in the order they were written. Never empty. */
    val transactions: List<Transaction>

    /** One transaction: a plain register, or the first cycle of a template just born. */
    data class Single(val transaction: Transaction) : TransactionRegistration {
        override val transactions: List<Transaction> get() = listOf(transaction)
    }

    /** The instalments of one purchase, one transaction per invoice they land on. */
    data class Installments(
        override val transactions: List<Transaction>,
    ) : TransactionRegistration
}
