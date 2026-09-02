package com.neoutils.finsight.ui.model

import androidx.compose.ui.graphics.Color
import com.neoutils.finsight.extension.DisplayAmount
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource

/**
 * A flat, display-ready view of a card invoice. Carries no domain graph: the status is
 * decomposed into the flat facts the card renders and gates on, computed by the mapper
 * from the domain rules (the UI consumes them, never re-derives them). A screen that
 * needs the domain `Invoice` to open a modal resolves it separately, by [id].
 */
data class InvoiceUi(
    val id: Long,
    // Denominated by the card, and mono-currency by construction: a card's currency is
    // fixed at creation and never changes (design D12/D17), so an invoice figure is a
    // single exact term and never wears the base currency (design D29).
    val amount: DisplayAmount,
    val totalUnpaidAmount: DisplayAmount,
    val availableLimit: DisplayAmount,
    val usagePercentage: Double,
    val showProgress: Boolean,
    val closingDate: LocalDate,
    val dueDate: LocalDate,
    val isClosable: Boolean,
    val canReopen: Boolean,
    val isOpen: Boolean,
    val isClosed: Boolean,
    val isRetroactive: Boolean,
    val isEditable: Boolean,
    /**
     * Whether this invoice has a payment to offer at all. A surface reads it instead of
     * enumerating statuses, so what is offered cannot drift from what the domain permits.
     */
    val canPay: Boolean,
    /**
     * The verb that names that payment — "advance" only while the cycle is still taking
     * spending, "pay" once it has ended. Meaningful only where [canPay] holds.
     */
    val payLabel: StringResource,
    /**
     * Whether paying discharges the invoice, rather than taking a part of what it owes.
     *
     * A discharge is the action the screen recommends and gives its solid emphasis to; a
     * part-payment is optional and stays outlined. The fact is the domain's, the emphasis
     * is the surface's.
     */
    val paySettles: Boolean,
    val statusColor: Color,
    val statusLabel: StringResource,
)
