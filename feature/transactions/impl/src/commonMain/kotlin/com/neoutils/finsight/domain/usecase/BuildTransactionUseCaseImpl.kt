@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.BuildTransactionError
import com.neoutils.finsight.domain.exception.BuildTransactionException
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.extension.contraLegFor
import com.neoutils.finsight.extension.moneyToDouble
import com.neoutils.finsight.util.dayMonthYear
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val currentDate
    get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

class BuildTransactionUseCaseImpl(
    private val getOrCreateInvoiceForMonthUseCase: GetOrCreateInvoiceForMonthUseCase
) : BuildTransactionUseCase {

    override suspend operator fun invoke(
        form: TransactionForm,
    ): Either<Throwable, TransactionIntent> = either {
        ensure(form.amount.isNotEmpty()) {
            BuildTransactionException(BuildTransactionError.AmountRequired)
        }

        ensure(form.amount.moneyToDouble() != 0.0) {
            BuildTransactionException(BuildTransactionError.AmountZero)
        }

        ensure(form.date.isNotEmpty()) {
            BuildTransactionException(BuildTransactionError.DateRequired)
        }

        ensure(!form.title.isNullOrEmpty() || form.category != null) {
            BuildTransactionException(BuildTransactionError.TitleOrCategoryRequired)
        }

        val date = catch { dayMonthYear.parse(form.date) }.bind()

        ensure(date <= currentDate) {
            BuildTransactionException(BuildTransactionError.DateFuture)
        }

        if (form.target.isAccount) {

            val account = ensureNotNull(form.account) {
                BuildTransactionException(BuildTransactionError.AccountRequired)
            }

            return@either TransactionIntent(
                title = form.title,
                date = date,
                legs = listOf(
                    TransactionLeg(
                        type = form.type,
                        amount = form.amount.moneyToDouble(),
                        accountId = account.id,
                    )
                ),
                contra = contraLegFor(form.type, form.category),
            )
        }

        ensure(form.type == TransactionType.EXPENSE) {
            BuildTransactionException(BuildTransactionError.CreditCardExpenseOnly)
        }

        val creditCard = ensureNotNull(form.creditCard) {
            BuildTransactionException(BuildTransactionError.CreditCardRequired)
        }

        val invoiceDueMonth = ensureNotNull(form.invoiceDueMonth) {
            BuildTransactionException(BuildTransactionError.InvoiceRequired)
        }

        val invoice = getOrCreateInvoiceForMonthUseCase(creditCard, invoiceDueMonth).bind()

        TransactionIntent(
            title = form.title,
            date = date,
            legs = listOf(
                TransactionLeg(
                    type = form.type,
                    amount = form.amount.moneyToDouble(),
                    // The card *is* its LIABILITY account, and the invoice *is* the
                    // dimension that leg carries. Resolving both is this caller's job
                    // now (design D6); the writer only sees identities.
                    accountId = creditCard.accountId,
                    dimensionId = invoice.dimensionId,
                )
            ),
            contra = contraLegFor(form.type, form.category),
        )
    }
}
