package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionTarget
import kotlinx.datetime.LocalDate

/**
 * Confirms one cycle of a recurring, optionally redirecting it to another account or
 * card and overriding what that cycle is called and how it is classified.
 *
 * **Every override applies to the confirmed cycle alone.** The template is read, never
 * written: a cycle that was in fact something else — another title, another category —
 * is a fact about that month, not a correction of the model. Whoever wants to change
 * the model edits the recurring.
 *
 * **A redirection to a different currency is refused, never converted** (design D17).
 * This is the one place a facade value could be written down as if it were another
 * currency: an omitted amount is the template's, and confirming a template created on a
 * BRL account against a USD one would record the raw number as dollars. Converting
 * instead would mean picking a rate on the user's behalf, mid-confirmation, in a
 * decision they neither asked for nor can see.
 *
 * The selector is what makes this unreachable by the designed path — it offers only
 * accounts of the template's own currency. This is the net behind it.
 *
 * ### What an omitted override means
 *
 * Every override is optional, and **the meaning of leaving one out is decided inside the
 * use case**, identically for every caller — no caller reads a default off the signature,
 * and none of them mean different things by silence. The meanings are not uniform because
 * absence does not mean the same thing about all of them:
 *
 * - [amount], [target], [account] and [creditCard] fall back to **what the template
 *   says**. Omitting them asks for the cycle the recurring describes, which is what a
 *   template is for.
 * - [title] and [category] fall back to **nothing**. Both are things the user can erase,
 *   and a cycle with no title of its own is displayed by its category — the app's one
 *   rule for reading titles. Substituting the template's back in would hand the user a
 *   name they had just erased, and would make "uncategorized" inexpressible.
 * - [invoice] falls back to the open invoice of the target card for the month, created
 *   if there is none.
 */
interface ConfirmRecurringUseCase {

    /**
     * The canonical form, and the one that carries the implementation.
     *
     * The template is resolved **when the operation runs**, so the cycle is posted
     * against the recurring as it is at that instant rather than as a sheet loaded it;
     * an identity that matches nothing is refused with `RecurringError.NOT_FOUND` and
     * nothing is written.
     */
    suspend operator fun invoke(
        recurringId: Long,
        date: LocalDate,
        amount: Double? = null,
        target: TransactionTarget? = null,
        account: Account? = null,
        creditCard: CreditCard? = null,
        invoice: Invoice? = null,
        title: String? = null,
        category: Category? = null,
    ): Either<Throwable, Transaction>

    /**
     * The convenience for a caller that already holds the recurring. It extracts the
     * identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(
        recurring: Recurring,
        date: LocalDate,
        amount: Double? = null,
        target: TransactionTarget? = null,
        account: Account? = null,
        creditCard: CreditCard? = null,
        invoice: Invoice? = null,
        title: String? = null,
        category: Category? = null,
    ): Either<Throwable, Transaction> = invoke(
        recurringId = recurring.id,
        date = date,
        amount = amount,
        target = target,
        account = account,
        creditCard = creditCard,
        invoice = invoice,
        title = title,
        category = category,
    )
}
