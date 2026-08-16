package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.TransactionRegistration
import com.neoutils.finsight.domain.model.form.TransactionForm

/**
 * Registers a filled form, whatever it turns out to describe.
 *
 * It owns the dispatch between the three: an instalment plan, a recurring template
 * opened by its first cycle, and a plain transaction. That decision has one owner
 * because it is domain and not presentation — a caller that chose the branch itself
 * would be a second copy of the rule, free to drift from this one, and every surface
 * that registers a transaction would carry its own.
 *
 * A split wins over the mark: paying in instalments is already a repetition, so a
 * form with more than one instalment is registered as one, whatever [isRecurring]
 * says. The two never arrive together from a screen — the sheet drops the mark when
 * the purchase is split — and the precedence is stated so that a caller which has no
 * such screen gets the same answer.
 *
 * [isRecurring] is a parameter rather than a field of [form] because that is where
 * the decision lives: the form describes a transaction, and nothing on it says the
 * user wants it repeated. It carries no default, so a caller states what it means
 * instead of inheriting an answer — the silent one would register a plain
 * transaction where a template was asked for, and nothing would look wrong.
 *
 * It answers what it wrote ([TransactionRegistration]), because a caller that cannot
 * name what it just created cannot report it either.
 */
interface RegisterTransactionUseCase {
    suspend operator fun invoke(
        form: TransactionForm,
        isRecurring: Boolean,
    ): Either<Throwable, TransactionRegistration>
}
