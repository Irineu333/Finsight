package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.BuildTransactionError
import com.neoutils.finsight.domain.exception.BuildTransactionException
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.extension.contraLegFor
import com.neoutils.finsight.extension.moneyToDouble

/**
 * The form's validity is [ValidateTransactionFormUseCase]'s answer, not a second reading of
 * it: this used to repeat all eight checks, and the screens repeated them a third time
 * through `TransactionForm.isValid`. What is left here is what only building does — resolving
 * the invoice, which touches the database and so cannot run while someone is still typing.
 */
class BuildTransactionUseCaseImpl(
    private val getOrCreateInvoiceForMonthUseCase: GetOrCreateInvoiceForMonthUseCase,
    private val validateTransactionForm: ValidateTransactionFormUseCase,
) : BuildTransactionUseCase {

    override suspend operator fun invoke(
        form: TransactionForm,
    ): Either<Throwable, TransactionIntent> = either {

        val date = validateTransactionForm(form)
            .mapLeft { BuildTransactionException(it) }
            .bind()

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
