# Color System

## Theme Setup

`FinsightTheme` wraps `MaterialTheme` with custom dark and light `ColorScheme`.
All color usage must go through `MaterialTheme.colorScheme` or the named tokens below.

```kotlin
// Always import via theme
import androidx.compose.material3.MaterialTheme.colorScheme
import com.neoutils.finsight.ui.theme.*
```

## Semantic ColorScheme Tokens

Use these for all structural/surface colors:

| Token | Dark | Light | Use |
|-------|------|-------|-----|
| `colorScheme.background` | `Surface1` (#0F172A) | `LightSurface2` | Screen background |
| `colorScheme.surface` | `Surface2` (#1E293B) | `LightCardBackground` | Card surfaces |
| `colorScheme.surfaceVariant` | `Surface3` (#334155) | `LightSurface2` | Secondary surfaces |
| `colorScheme.surfaceContainer` | `Surface2` | `LightCardBackground` | Card containers |
| `colorScheme.surfaceContainerHighest` | `Surface3` | `LightSurface2` | Elevated containers |
| `colorScheme.onSurface` | White | `LightTextPrimary` | Primary text |
| `colorScheme.onSurfaceVariant` | `TextLight1` | `LightTextSecondary` | Secondary text |
| `colorScheme.primary` | `Primary1` (Teal) | `Primary1` | Actions, buttons, FAB |
| `colorScheme.outline` | `DividerColor` | `LightDivider` | `HorizontalDivider`, borders |
| `colorScheme.outlineVariant` | `TextLight2` | `LightDividerVariant` | Subtle borders |
| `colorScheme.error` | `Error` (Dark red) | `Error` | Error states, destructive buttons |

## Financial Semantic Colors

These are **fixed across both themes** — they carry financial meaning, not structural meaning.
Always use these for financial values, never `colorScheme.primary` or arbitrary colors.

```kotlin
import com.neoutils.finsight.ui.theme.Income        // #22C55E — Receitas
import com.neoutils.finsight.ui.theme.Expense       // #EF4444 — Despesas
import com.neoutils.finsight.ui.theme.Adjustment    // #F59E0B — Ajustes
import com.neoutils.finsight.ui.theme.InvoicePayment // #8B5CF6 — Pagamento de Fatura
import com.neoutils.finsight.ui.theme.CategoryColor  // #3B82F6 — Categorias
```

## Status Colors

For feedback states (success messages, warnings, info banners):

```kotlin
import com.neoutils.finsight.ui.theme.Success   // #14B8A6 — Teal
import com.neoutils.finsight.ui.theme.Warning   // #F59E0B — Amber
import com.neoutils.finsight.ui.theme.Error     // #DC2626 — Red
import com.neoutils.finsight.ui.theme.Info      // #3B82F6 — Blue
```

## Card Background Colors (Tinted)

For cards that need a subtle financial color tint:

```kotlin
// Dark theme
IncomeCardBackground    // #1E3A2E — dark green tint
ExpenseCardBackground   // #3A1E1E — dark red tint
AdjustmentCardBackground // #3A2E1E — dark amber tint

// Light theme
IncomeCardBackgroundLight     // #DCFCE7
ExpenseCardBackgroundLight    // #FEE2E2
AdjustmentCardBackgroundLight // #FEF3C7
```

In practice, prefer `Income.copy(alpha = 0.15f)` for tinted backgrounds in components
(as done in `BalanceCardConfig`) — it automatically adapts to the theme surface.

## Budget Progress Color

Use the provided utility function — never interpolate manually:

```kotlin
import com.neoutils.finsight.ui.theme.budgetProgressColor

val color = budgetProgressColor(progress) // 0f → Success, 0.5f → Warning, 1f → Error
```

## Anti-patterns

```kotlin
// ❌ Hardcoded hex
Text(color = Color(0xFF22C55E))            // use Income
Card(containerColor = Color(0xFF1E293B))   // use colorScheme.surfaceContainer

// ❌ Wrong token for financial color
Text(color = colorScheme.primary)          // ❌ for income amounts — use Income

// ❌ Using dark-only named tokens directly
Surface(color = Surface2)                  // ❌ breaks light theme — use colorScheme.surfaceContainer

// ✅ Correct
Text(color = Income)                       // financial income value
Card(containerColor = colorScheme.surfaceContainer) // structural card
```
