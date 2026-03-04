# Existing Components

Before creating a new component, check if one already exists.
Components live in `ui/component/`.

## Available Components

| Component | File | Use |
|-----------|------|-----|
| `OperationCard` | `OperationCard.kt` | Transaction/operation list item |
| `BalanceCard` | `BalanceCard.kt` | Balance/summary display card with multiple configs |
| `SummaryCard` | `SummaryCard.kt` | Month financial overview (income/expense/balance rows) |
| `CategoryCard` | `CategoryCard.kt` | Category display card |
| `CategorySpendingCard` | `CategorySpendingCard.kt` | Category with spending progress |
| `AccountSelector` | `AccountSelector.kt` | Dropdown/selector for accounts |
| `CategorySelector` | `CategorySelector.kt` | Single category picker |
| `CreditCardSelector` | `CreditCardSelector.kt` | Credit card picker |
| `TargetSelector` | `TargetSelector.kt` | Account/credit card target picker |
| `InvoiceSelector` | `InvoiceSelector.kt` | Invoice month picker |
| `MonthSelector` | `MonthSelector.kt` | Month navigation selector |
| `MonthPickerDropdownMenu` | `MonthPickerDropdownMenu.kt` | Dropdown month picker |
| `InvoiceMonthNavigator` | `InvoiceMonthNavigator.kt` | Prev/Next invoice month navigation |
| `InstallmentCounter` | `InstallmentCounter.kt` | Installment count input |
| `BottomNavigationBar` | `BottomNavigationBar.kt` | App bottom nav |
| `CreditCardUI` | `CreditCardUI.kt` | Visual credit card display |
| `DashboardCreditCardUi` | `DashboardCreditCardUi.kt` | Dashboard credit card summary |
| `InvoiceSummaryCard` | `InvoiceSummaryCard.kt` | Invoice summary display |
| `CreditCardTotalSummaryCard` | `CreditCardTotalSummaryCard.kt` | Credit card total summary |
| `SharedTransitionProvider` | `SharedTransitionProvider.kt` | Shared element transition wrapper |

## ModalManager & Navigation

| Component | File | Use |
|-----------|------|-----|
| `ModalManager` | `ModalManager.kt` | Modal stack manager (Koin single) |
| `ModalManagerHost` | `ModalManager.kt` | Root composable — wraps app content |
| `LocalModalManager` | `ModalManager.kt` | CompositionLocal to access ModalManager |
| `LocalNavigator` | `ModalManager.kt` | CompositionLocal for in-modal navigation |
| `Navigator` | `ModalManager.kt` | Navigation action dispatcher |
| `NavigationAction` | `ModalManager.kt` | Sealed class of navigation destinations |

## Button Patterns

### Primary action (submit, confirm)
```kotlin
Button(
    onClick = { ... },
    enabled = uiState.canSubmit,
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp)
) {
    Text(text = "Salvar", fontWeight = FontWeight.Bold)
}
```

### Destructive action (delete, cancel invoice)
```kotlin
Button(
    onClick = { ... },
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error)
) {
    Text(text = "Excluir", fontWeight = FontWeight.Bold)
}
```

### Secondary outline action
```kotlin
OutlinedButton(
    onClick = { ... },
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(8.dp),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.primary),
    border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.5f))
) {
    Text(text = "Pagar Fatura")
}
```

## TextField Pattern

```kotlin
OutlinedTextField(
    state = textFieldState,
    label = { Text(text = stringResource(Res.string.label)) },
    keyboardOptions = KeyboardOptions(
        capitalization = KeyboardCapitalization.Sentences,
        imeAction = ImeAction.Done
    ),
    isError = uiState.validation[Field.NAME] is Validation.Error,
    supportingText = when (val v = uiState.validation[Field.NAME]) {
        is Validation.Error -> { { Text(text = stringUiText(v.error)) } }
        else -> null
    },
    shape = RoundedCornerShape(12.dp),
    lineLimits = TextFieldLineLimits.SingleLine,
    modifier = Modifier
        .animateContentSize()
        .fillMaxWidth(),
)
```

Key details:
- Always `shape = RoundedCornerShape(12.dp)`
- Always `lineLimits = TextFieldLineLimits.SingleLine` for single-line fields
- Always `.animateContentSize()` when `supportingText` is shown conditionally
- Use `snapshotFlow { state.text.toString() }.drop(1)` for text change observation

## Loading State

```kotlin
// Inside a button or trailing icon
CircularProgressIndicator(
    modifier = Modifier.size(20.dp),
    strokeWidth = 2.dp
)
```

## Dividers

```kotlin
HorizontalDivider() // uses colorScheme.outline automatically
```

## Icon with Semantic Color

```kotlin
// Financial operation icon box
Surface(
    color = semanticColor.copy(alpha = 0.2f),
    shape = MaterialTheme.shapes.medium,
    modifier = Modifier.size(48.dp)
) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = semanticColor,
            modifier = Modifier.size(24.dp)
        )
    }
}
```
