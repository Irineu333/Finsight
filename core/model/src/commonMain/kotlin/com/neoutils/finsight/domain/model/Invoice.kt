@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.model

import com.neoutils.finsight.extension.safeOnDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class Invoice(
    val id: Long = 0,
    val creditCard: CreditCard,
    // The ledger identity this invoice's legs are tagged with. Nullable only
    // because v10 added the column to existing rows; every invoice has one.
    val dimensionId: Long? = null,
    val openingMonth: YearMonth,
    val closingMonth: YearMonth,
    val dueMonth: YearMonth,
    val status: Status,
    val createdAt: Instant = Clock.System.now(),
    val openedAt: LocalDate? = null,
    val closedAt: LocalDate? = null,
    val paidAt: LocalDate? = null
) {
    val openingDate get() = window.openingDate
    val closingDate get() = window.closingDate
    val dueDate get() = dueMonth.safeOnDay(creditCard.dueDay)

    val isClosable get() = when(status) {
        Status.OPEN -> true
        Status.RETROACTIVE -> true
        else -> false
    }

    /**
     * Whether this invoice may be marked `PAID`.
     *
     * `RETROACTIVE` belongs here because a past cycle that owes nothing is settled by
     * closing it (`CloseInvoiceUseCase`), which marks it paid while it is still
     * retroactive. It is the domain's own question — *who may become `PAID`* — and not
     * the one a screen asks before offering a payment; for that, see [acceptsPayment].
     */
    val isPayable get() = when(status) {
        Status.CLOSED -> true
        Status.RETROACTIVE -> true
        else -> false
    }

    /**
     * Whether this invoice takes a payment of an amount the user states, capped at what
     * it owes, leaving its status untouched.
     *
     * An invoice that still receives spending has no final figure to settle, so what is
     * paid into it is a part and never a discharge.
     */
    val acceptsPartialPayment get() = when (status) {
        Status.OPEN -> true
        Status.RETROACTIVE -> true
        else -> false
    }

    /**
     * Whether this invoice takes only the whole of what it owes, and is discharged by it.
     *
     * A closed invoice has a final figure, which is what makes a partial amount
     * inexpressible here rather than merely unoffered.
     */
    val acceptsFullSettlement get() = status == Status.CLOSED

    /**
     * Whether this invoice takes a payment at all — the single filter every surface that
     * offers one reads, and the one that decides which invoices a payment may name.
     *
     * `FUTURE` is out because its cycle has not begun and `PAID` because it is frozen.
     */
    val acceptsPayment get() = acceptsPartialPayment || acceptsFullSettlement

    /**
     * Fatura fechável na data [date]: além do status permitir ([isClosable]), a data de
     * fechamento já precisa ter chegado — para `OPEN` e `RETROACTIVE` igualmente. É a única
     * definição do predicado com corte de data; as telas a consomem em vez de reescrevê-la.
     */
    fun isClosableOn(date: LocalDate) = isClosable && date >= closingDate

    enum class Status {
        FUTURE,
        OPEN,
        CLOSED,
        PAID,
        RETROACTIVE;

        val isFuture: Boolean
            get() = this == FUTURE

        val isOpen: Boolean
            get() = this == OPEN

        val isClosed: Boolean
            get() = this == CLOSED

        val isPaid: Boolean
            get() = this == PAID

        val isRetroactive: Boolean
            get() = this == RETROACTIVE

        /**
         * Closed to new spending. `CLOSED` and `PAID` coincide here and only here:
         * a closed invoice still accepts the payment that settles it, and a paid one
         * accepts nothing — the distinction lives at the write boundary (design D23).
         */
        val isClosedToNewExpenses: Boolean
            get() = this == CLOSED || this == PAID

        val isEditable: Boolean
            get() = this == RETROACTIVE ||
                    this == OPEN ||
                    this == FUTURE

        val isDeletable: Boolean
            get() = this == FUTURE ||
                    this == RETROACTIVE
    }

    init {
        require(closingMonth > openingMonth) {
            "Closing month must be after opening month"
        }
        require(dueMonth >= closingMonth) {
            "Due month must be equal to or after closing month"
        }
    }
}

/**
 * The invoice that closing this one opened, at `openingMonth == this.closingMonth`.
 * Reopening demotes it back to `FUTURE`, so it is the pivot of the reopen rule.
 */
fun Invoice.reopenSuccessor(cardInvoices: List<Invoice>): Invoice? =
    cardInvoices.find { it.openingMonth == closingMonth }

/**
 * Reopening is valid only for the latest closed invoice — the one whose successor is
 * the current `OPEN` one. Any earlier closed (or formerly-retroactive) invoice has a
 * later cycle already active or settled after it, so reopening would leave two `OPEN`
 * invoices on the card. `ReopenInvoiceUseCase` enforces this; the screens read it to
 * not offer what the domain refuses, instead of re-deciding the rule themselves.
 */
fun Invoice.isReopenable(cardInvoices: List<Invoice>): Boolean =
    status != Invoice.Status.OPEN &&
    status != Invoice.Status.PAID &&
    reopenSuccessor(cardInvoices)?.status == Invoice.Status.OPEN

