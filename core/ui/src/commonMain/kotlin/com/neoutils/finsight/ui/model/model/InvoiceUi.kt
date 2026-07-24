package com.neoutils.finsight.ui.model

import androidx.compose.ui.graphics.Color
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
    val amount: Double,
    val totalUnpaidAmount: Double,
    val availableLimit: Double,
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
