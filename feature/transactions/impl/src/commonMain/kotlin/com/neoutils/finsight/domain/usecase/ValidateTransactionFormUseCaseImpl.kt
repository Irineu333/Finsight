@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.BuildTransactionError
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.extension.moneyToDouble
import com.neoutils.finsight.extension.today
import com.neoutils.finsight.util.dayMonthYear
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ValidateTransactionFormUseCaseImpl(
    private val clock: Clock,
) : ValidateTransactionFormUseCase {

    override operator fun invoke(
        form: TransactionForm,
    ): Either<BuildTransactionError, LocalDate> = either {

        // Not a second copy of the closure invariant — that one lives on the `Account` and is
        // enforced at the write boundary, which stays. This is the form declining to offer a
        // submit the ledger is known to refuse.
        ensure(form.archivedSelections.isEmpty()) {
            BuildTransactionError.ClosedSelection
        }

        ensure(form.amount.isNotEmpty()) { BuildTransactionError.AmountRequired }
        ensure(form.amount.moneyToDouble() != 0.0) { BuildTransactionError.AmountZero }
        ensure(form.date.isNotEmpty()) { BuildTransactionError.DateRequired }

        ensure(!form.title.isNullOrEmpty() || form.category != null) {
            BuildTransactionError.TitleOrCategoryRequired
        }

        val date = ensureNotNull(
            runCatching { dayMonthYear.parse(form.date) }.getOrNull()
        ) {
            BuildTransactionError.DateInvalid
        }

        ensure(date <= clock.today()) { BuildTransactionError.DateFuture }

        if (form.target.isAccount) {
            ensureNotNull(form.account) { BuildTransactionError.AccountRequired }
            return@either date
        }

        ensure(form.type == TransactionType.EXPENSE) {
            BuildTransactionError.CreditCardExpenseOnly
        }

        ensureNotNull(form.creditCard) { BuildTransactionError.CreditCardRequired }
        ensureNotNull(form.invoiceDueMonth) { BuildTransactionError.InvoiceRequired }

        date
    }
}
