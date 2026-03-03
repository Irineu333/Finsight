# Spacing & Layout

## Spacing Rhythm

All padding and spacing values must be **multiples of 4dp**.

Standard breakpoints used throughout the project:

| Value | Use |
|-------|-----|
| `4.dp` | Icon-to-label gaps, tight inline spacing |
| `8.dp` | Spacing between related elements in a Row |
| `12.dp` | Card internal spacing between rows (`Arrangement.spacedBy(12.dp)`) |
| `16.dp` | Standard section spacing, vertical arrangement in modals |
| `20.dp` | Card padding (medium cards: `PaddingValues(20.dp)`) |
| `24.dp` | Modal horizontal padding, large card padding |
| `32.dp` | Modal bottom padding (`padding(bottom = 32.dp)`) |
| `48.dp` | Icon box size in `OperationCard` |

## Standard Padding Patterns

### Cards
```kotlin
// Standard card internal padding
.padding(20.dp)         // medium card (SummaryCard)
PaddingValues(24.dp)    // large card (BalanceCard default)
PaddingValues(16.dp)    // compact card (BalanceCard.Income/Expense)
.padding(12.dp)         // list item card (OperationCard)
```

### Modals (BottomSheet)
```kotlin
// All modal content columns use:
.padding(horizontal = 24.dp)
.padding(bottom = 32.dp)

// Internal vertical spacing between form elements:
verticalArrangement = Arrangement.spacedBy(16.dp)
```

### Icon Sizes
```kotlin
Modifier.size(48.dp)  // primary icon box (OperationCard)
Modifier.size(24.dp)  // icon inside a 48dp box
Modifier.size(20.dp)  // badge/overlay icon (credit card badge)
Modifier.size(18.dp)  // inline icon (navigate arrow, edit)
Modifier.size(16.dp)  // small inline icon
```

## Layout Patterns

### Screen-level layout
```kotlin
Scaffold(
    topBar = { ... },
    floatingActionButton = { ... },
) { paddingValues ->
    LazyColumn(
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // items
    }
}
```

### Card Row (label + value)
```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    Text(text = label, style = MaterialTheme.typography.bodyMedium)
    Text(text = value, style = MaterialTheme.typography.titleMedium)
}
```

### Card with icon + content + trailing value (OperationCard pattern)
```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    // Icon box — 48dp
    Surface(modifier = Modifier.size(48.dp), shape = MaterialTheme.shapes.medium) { ... }

    // Content — weight(1f)
    Column(modifier = Modifier.weight(1f)) { ... }

    // Trailing value
    Text(text = amount, ...)
}
```

### Scrollable modal content
```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp)
        .padding(bottom = 32.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
) { ... }
```

## Shapes

| Shape | Value | Use |
|-------|-------|-----|
| `MaterialTheme.shapes.large` | ~16dp (M3 default) | Cards, BalanceCard |
| `RoundedCornerShape(16.dp)` | 16dp | SummaryCard, custom cards |
| `RoundedCornerShape(12.dp)` | 12dp | Buttons, TextFields, OperationCard |
| `MaterialTheme.shapes.medium` | ~8dp | Icon boxes inside cards |
| `CircleShape` | full radius | Badge overlays, avatar circles |
| `RoundedCornerShape(8.dp)` | 8dp | OutlinedButton in BalanceCard |
| `RoundedCornerShape(4.dp)` | 4dp | Clickable text areas (edit row clip) |
