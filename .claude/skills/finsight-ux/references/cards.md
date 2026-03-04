# Card Patterns

## Base Card Rules

All cards use `CardDefaults.cardColors` with `containerColor = colorScheme.surfaceContainer`.
Never use raw `Surface2`, `CardBackground`, or hardcoded colors for cards.

```kotlin
Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(
        containerColor = colorScheme.surfaceContainer,
        contentColor = colorScheme.onSurface,
    ),
    shape = RoundedCornerShape(16.dp), // or shapes.large
) { ... }
```

## OperationCard — List Item Card

Pattern: icon box (48dp) + content column (weight 1f) + trailing amount.

```kotlin
Card(
    onClick = onClick,
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer),
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon box — always 48dp, shape = shapes.medium
        Surface(
            color = semanticColor.copy(alpha = 0.2f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(imageVector = ..., tint = semanticColor, modifier = Modifier.size(24.dp))
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant)
        }

        Text(text = amount, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = semanticColor))
    }
}
```

## BalanceCard — Summary/Balance Display

Use `BalanceCardConfig` to define display variants. Never recreate this pattern inline.

Available configs (all `@Composable` properties on companion object):
- `BalanceCardConfig.Default` — large balance (36sp bold), `padding(24.dp)`, `shapes.large`
- `BalanceCardConfig.Income` — tinted green, 20sp bold, `padding(16.dp)`
- `BalanceCardConfig.Expense` — tinted red, 20sp bold, `padding(16.dp)`
- `BalanceCardConfig.Payment` — tinted purple, 20sp bold, `padding(16.dp)`
- `BalanceCardConfig.CreditCard` — 28sp bold, `padding(20.dp)`

```kotlin
BalanceCard(
    balance = uiState.balance,
    config = BalanceCardConfig.Default,
    onEditClick = viewModel::onEditBalance,
    modifier = Modifier.fillMaxWidth()
)
```

## SummaryCard — Financial Overview

For month/period summaries with multiple rows. Uses `RoundedCornerShape(16.dp)`, `padding(20.dp)`.
Internal rows use `Arrangement.spacedBy(12.dp)`.

Key pattern: `SummaryRow` composable with `SignDisplay` enum controlling `+/-` prefix.

```kotlin
SummaryCard(
    balanceOverview = uiState.balanceOverview,
    isCurrentMonth = uiState.isCurrentMonth,
    onEditBalance = viewModel::onEditBalance,
    modifier = Modifier.fillMaxWidth()
)
```

## Tinted Financial Cards (BalanceCard.Income/Expense pattern)

When you need a card with financial color tint:

```kotlin
// PREFERRED: use .copy(alpha = 0.15f) — adapts to both themes
containerColor = Income.copy(alpha = 0.15f)
containerColor = Expense.copy(alpha = 0.15f)
containerColor = InvoicePayment.copy(alpha = 0.15f)

// AVOID: hardcoded background tokens
containerColor = IncomeCardBackground  // ❌ dark-only
```

## Cards with Config Pattern

For components with multiple display variants, use a `data class XxxConfig` with `companion object`
providing `@Composable` named configs. See `BalanceCardConfig` and `SummaryRowConfig` as templates.

```kotlin
data class MyCardConfig(
    val style: TextStyle,
    val container: Color,
    val padding: PaddingValues,
) {
    companion object {
        val Default @Composable get() = MyCardConfig(
            style = MaterialTheme.typography.titleMedium,
            container = colorScheme.surfaceContainer,
            padding = PaddingValues(16.dp),
        )
        val Highlighted @Composable get() = MyCardConfig(
            style = MaterialTheme.typography.titleMedium.copy(color = Income),
            container = Income.copy(alpha = 0.15f),
            padding = PaddingValues(16.dp),
        )
    }
}
```
