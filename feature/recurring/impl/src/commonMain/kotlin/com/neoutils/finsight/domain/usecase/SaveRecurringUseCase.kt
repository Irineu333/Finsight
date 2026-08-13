@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import com.neoutils.finsight.domain.exception.RecurringException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.form.RecurringForm
import com.neoutils.finsight.domain.repository.IRecurringRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class SaveRecurringUseCase(
    private val repository: IRecurringRepository,
) {
    suspend operator fun invoke(
        id: Long = 0,
        type: TransactionType,
        amount: String,
        title: String?,
        dayOfMonth: String,
        category: Category?,
        account: Account?,
        creditCard: CreditCard?,
        createdAt: Long? = null,
        isArchived: Boolean = false,
    ): Either<Throwable, Unit> = either {

        // The rules a template has to satisfy live with the form (one owner); what is
        // decided here is only what the form has no way to know — the identity of an
        // edit and the archived flag it carries over.
        val recurring = RecurringForm(
            type = type,
            amount = amount,
            title = title.orEmpty(),
            dayOfMonth = dayOfMonth,
            account = account,
            creditCard = creditCard,
            category = category,
        ).toRecurring(
            createdAt = createdAt ?: Clock.System.now().toEpochMilliseconds(),
        ).mapLeft {
            RecurringException(it)
        }.bind().copy(
            id = id,
            isArchived = isArchived,
        )

        catch {
            if (id == 0L) repository.insert(recurring)
            else repository.update(recurring)
        }.bind()
    }
}
