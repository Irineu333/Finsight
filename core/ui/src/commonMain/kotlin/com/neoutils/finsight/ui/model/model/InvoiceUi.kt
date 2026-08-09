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
    val statusColor: Color,
    val statusLabel: StringResource,
)
