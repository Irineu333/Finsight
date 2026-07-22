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
    val openingDate get() = openingMonth.safeOnDay(creditCard.closingDay)
    val closingDate get() = closingMonth.safeOnDay(creditCard.closingDay)
    val dueDate get() = dueMonth.safeOnDay(creditCard.dueDay)

    val isClosable get() = when(status) {
        Status.OPEN -> true
        Status.RETROACTIVE -> true
        else -> false
    }

    val isPayable get() = when(status) {
        Status.CLOSED -> true
        Status.RETROACTIVE -> true
        else -> false
    }

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

