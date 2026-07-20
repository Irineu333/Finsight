package com.neoutils.finsight.ui.extension

import androidx.compose.ui.graphics.Color
import com.neoutils.finsight.domain.model.Invoice

/**
 * The single owner of the invoice status palette. It lives here, and not on the enum,
 * because `core/model` is the domain: it must not know how a status is painted.
 */
val Invoice.Status.color: Color
    get() = when (this) {
        Invoice.Status.FUTURE -> Color(0xFF42A5F5)
        Invoice.Status.OPEN -> Color(0xFFFFA726)
        Invoice.Status.CLOSED -> Color(0xFFEF5350)
        Invoice.Status.PAID -> Color(0xFF66BB6A)
        Invoice.Status.RETROACTIVE -> Color(0xFF5C6BC0)
    }
