package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import com.neoutils.finsight.domain.model.TransactionRegistration
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.domain.repository.ITransactionRepository

class RegisterTransactionUseCaseImpl(
    private val transactionRepository: ITransactionRepository,
    private val buildTransaction: BuildTransactionUseCase,
    private val addInstallment: AddInstallmentUseCase,
    private val startRecurringFromTransaction: StartRecurringFromTransactionUseCase,
) : RegisterTransactionUseCase {

    override suspend fun invoke(
        form: TransactionForm,
        isRecurring: Boolean,
    ): Either<Throwable, TransactionRegistration> = either {
        // The split is read before anything is built: the instalment path builds the
        // base intent itself, once, and fans it out over the invoices it resolves.
        if (form.installments > 1) {
            return@either TransactionRegistration.Installments(
                addInstallment(form, form.installments).bind()
            )
        }

        val intent = buildTransaction(form).bind()

        TransactionRegistration.Single(
            if (isRecurring) {
                // The intent is completed, never rebuilt: it already carries the invoice
                // the caller picked, and how a recurring is born is the recurring
                // feature's rule, consumed here.
                startRecurringFromTransaction(
                    form = form.asRecurringOn(intent.date),
                    firstCycle = intent,
                ).bind()
            } else {
                catch { transactionRepository.createTransaction(intent) }.bind()
            }
        )
    }
}
