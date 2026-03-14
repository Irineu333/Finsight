package com.neoutils.finsight.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// Background colors
val Surface1 = Color(0xFF0F172A)
val Surface2 = Color(0xFF1E293B)
val Surface3 = Color(0xFF334155)

// Primary colors
val Primary1 = Color(0xFF0D9A8E) // Teal - Success/Primary actions

// Income/Expense colors
val Income = Color(0xFF22C55E) // Vibrant lime green - Receitas
val Expense = Color(0xFFEF4444) // Vibrant red - Despesas
val Adjustment = Color(0xFFF59E0B) // Amber - Ajustes
val InvoicePayment = Color(0xFF8B5CF6) // Purple - Pagamento de Fatura

// Card backgrounds
val IncomeCardBackground = Color(0xFF1E3A2E) // Dark lime-green tint
val ExpenseCardBackground = Color(0xFF3A1E1E) // Dark red tint
val AdjustmentCardBackground = Color(0xFF3A2E1E) // Dark amber tint

// Text colors
val TextLight1 = Color(0xFF94A3B8)
val TextLight2 = Color(0xFF64748B)

// Component colors
val CardBackground = Color(0xFF1E293B)
val DividerColor = Color(0xFF334155)
val DividerColorVariant = Color(0xFF64748B)
val IconTint = Color(0xFF94A3B8)

// Status colors
val Success = Color(0xFF14B8A6) // Teal - Sucesso/Salvamento
val Error = Color(0xFFDC2626) // Dark red - Erros críticos
val Warning = Color(0xFFF59E0B) // Amber - Avisos
val Info = Color(0xFF3B82F6) // Blue - Informações

// Budget progress color: smooth gradient Success → Warning → Error
fun budgetProgressColor(progress: Float): Color = when {
    progress >= 1f -> Error
    progress >= 0.5f -> lerp(Warning, Error, (progress - 0.5f) / 0.5f)
    else -> lerp(Success, Warning, progress / 0.5f)
}

// Goal progress color: smooth gradient Error → Warning → Success
fun goalProgressColor(progress: Float): Color = when {
    progress >= 1f -> Success
    progress >= 0.5f -> lerp(Warning, Success, (progress - 0.5f) / 0.5f)
    else -> lerp(Error, Warning, progress / 0.5f)
}

// Category color
val CategoryColor = Color(0xFF3B82F6)

// Light theme surfaces
val LightSurface1 = Color(0xFFF8FAFC)   // Slate-50
val LightSurface2 = Color(0xFFF1F5F9)   // Slate-100
val LightSurface3 = Color(0xFFE2E8F0)   // Slate-200

// Light theme card backgrounds
val IncomeCardBackgroundLight = Color(0xFFDCFCE7)       // Green-100
val ExpenseCardBackgroundLight = Color(0xFFFEE2E2)      // Red-100
val AdjustmentCardBackgroundLight = Color(0xFFFEF3C7)   // Amber-100

// Light theme text / component
val LightTextPrimary = Color(0xFF0F172A)    // Slate-900
val LightTextSecondary = Color(0xFF475569)  // Slate-600
val LightDivider = Color(0xFFCBD5E1)        // Slate-300
val LightDividerVariant = Color(0xFF94A3B8) // Slate-400
val LightIconTint = Color(0xFF64748B)       // Slate-500
val LightCardBackground = Color(0xFFFFFFFF)
