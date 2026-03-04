# Typography

## Scale Reference (`AppTypography`)

Always use `MaterialTheme.typography.*` — never set `fontSize` directly in ad-hoc composables.

| Token | Size | Weight | Line | Use |
|-------|------|--------|------|-----|
| `displayLarge` | 57sp | Bold | 64sp | Large hero numbers |
| `displayMedium` | 45sp | Bold | 52sp | — |
| `displaySmall` | 36sp | Bold | 44sp | Main balance figures |
| `headlineLarge` | 32sp | Bold | 40sp | — |
| `headlineMedium` | 28sp | SemiBold | 36sp | Section headings |
| `headlineSmall` | 24sp | SemiBold | 32sp | Modal titles (primary) |
| `titleLarge` | 22sp | SemiBold | 28sp | Screen titles |
| `titleMedium` | 16sp | Medium | 24sp | Card titles, form labels |
| `titleSmall` | 14sp | Medium | 20sp | Secondary labels |
| `bodyLarge` | 16sp | Normal | 24sp | Primary body text |
| `bodyMedium` | 14sp | Normal | 20sp | Secondary body, descriptions |
| `bodySmall` | 12sp | Normal | 16sp | Captions, hints |
| `labelLarge` | 14sp | Medium | 20sp | Button text |
| `labelMedium` | 12sp | Medium | 16sp | Chips, badges |
| `labelSmall` | 11sp | Medium | 16sp | Micro labels |

## Usage Patterns in the Project

```kotlin
// Modal title
Text(
    text = "Excluir Transação",
    style = MaterialTheme.typography.headlineSmall, // 24sp SemiBold
    color = colorScheme.onSurface
)

// Card title / item label
Text(
    text = operation.label,
    style = MaterialTheme.typography.titleMedium, // 16sp Medium
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
)

// Secondary info (date, subtitle)
Text(
    text = date,
    style = MaterialTheme.typography.bodyMedium, // 14sp Normal
    color = colorScheme.onSurfaceVariant
)

// Amount value (financial)
Text(
    text = formatter.format(amount),
    style = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.SemiBold,
        color = Income
    )
)
```

## When Direct `fontSize` Is Acceptable

Components with a `Config` data class (like `BalanceCardConfig`, `SummaryRowConfig`) may use
direct `fontSize` inside the config's `TextStyle` definition. This is intentional — these
components have specific sizing requirements that vary by config variant.

```kotlin
// ACCEPTABLE — inside a config object
val Default @Composable get() = BalanceCardConfig(
    style = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold, ...)
)

// NOT ACCEPTABLE — in a regular composable
@Composable
fun MyCard() {
    Text(fontSize = 18.sp) // ❌ use MaterialTheme.typography instead
}
```

## Color in Text

Pair typography tokens with the correct color:

| Text role | Color |
|-----------|-------|
| Primary text | `colorScheme.onSurface` |
| Secondary / hint | `colorScheme.onSurfaceVariant` |
| Income amount | `Income` |
| Expense amount | `Expense` |
| Adjustment amount | `Adjustment` |
| Invoice payment | `InvoicePayment` |
| Error message | `colorScheme.error` |
| Disabled / muted | `colorScheme.onSurface.copy(alpha = 0.38f)` |
