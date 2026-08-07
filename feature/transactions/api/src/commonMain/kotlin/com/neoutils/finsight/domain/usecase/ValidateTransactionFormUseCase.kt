package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.error.BuildTransactionError
import com.neoutils.finsight.domain.model.form.TransactionForm
import kotlinx.datetime.LocalDate

/**
 * Whether a [TransactionForm] describes a transaction that can be written — and, when it
 * does, the date it means.
 *
 * It is a use case rather than a method on the form because the rule needs a clock, and a
 * data class has nowhere to keep one. It used to take today as a parameter, defaulted to
 * `Clock.System`: a caller that forgot it silently judged against a different today from
 * the rest of the app, which is exactly the bug a build that moves time exposes. Here the
 * clock is a constructor dependency, so there is no wrong way left to call this.
 *
 * It is pure and not suspend: it may be called on every keystroke, to decide whether a
 * screen offers its submit at all.
 *
 * Returning the parsed [LocalDate] is what lets [BuildTransactionUseCase] consume this
 * instead of repeating it — the date is the one thing validating already computes and
 * building needs. Everything the build adds beyond this needs I/O (the invoice), which is
 * precisely what must not happen while someone is still typing.
 */
interface ValidateTransactionFormUseCase {
    operator fun invoke(form: TransactionForm): Either<BuildTransactionError, LocalDate>
}
