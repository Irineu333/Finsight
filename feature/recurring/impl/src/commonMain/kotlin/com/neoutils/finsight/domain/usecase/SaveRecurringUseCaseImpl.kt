@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.RecurringError
import com.neoutils.finsight.domain.exception.RecurringException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.form.RecurringForm
import com.neoutils.finsight.domain.repository.IRecurringRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class SaveRecurringUseCaseImpl(
    private val repository: IRecurringRepository,
) : SaveRecurringUseCase {

    override suspend fun invoke(
        id: Long,
        type: TransactionType,
        amount: String,
        title: String?,
        dayOfMonth: String,
        category: Category?,
        account: Account?,
        creditCard: CreditCard?,
        createdAt: Long?,
        isArchived: Boolean,
    ): Either<Throwable, Unit> = either {

        // Editing is a blind `UPDATE` by id, which touches nothing when the id matches
        // nothing: without this the caller would be told the template was edited. A `0`
        // is the absence of an identity — it creates one — so there is nothing to check.
        if (id != NEW_RECURRING) {
            ensureNotNull(catch { repository.getRecurringById(id) }.bind()) {
                RecurringException(RecurringError.NOT_FOUND)
            }
        }

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
            if (id == NEW_RECURRING) repository.insert(recurring)
            else repository.update(recurring)
        }.bind()
    }

    private companion object {
        /** The absence of an identity: the template does not exist yet. */
        const val NEW_RECURRING = 0L
    }
}
