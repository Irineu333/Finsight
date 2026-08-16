package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.TransactionType

/**
 * Writes a recurring template — the one door both creating and editing one go through.
 *
 * The rules a template has to satisfy live with `RecurringForm` (one owner); what is
 * decided here is only what the form has no way to know: the identity of an edit and the
 * archived flag it carries over.
 */
interface SaveRecurringUseCase {

    /**
     * [id] is the identity, and `0` is the absence of one: it creates the template this
     * operates on, so there is nothing to resolve. Any other identity is resolved **when
     * the operation runs** and edited as it is at that instant; one that matches nothing
     * is refused with `RecurringError.NOT_FOUND`, because writing by id touches no row
     * when the id matches none and the caller would otherwise be told the edit landed.
     */
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
    ): Either<Throwable, Unit>
}
