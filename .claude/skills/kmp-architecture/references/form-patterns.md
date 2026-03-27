# Form Pattern (Modal Forms)

## Purpose

This document defines the architecture pattern for forms used in modal bottom sheets.
It is prescriptive for new form features in `ui/modal/*`.

Primary examples in current code:
- `CreditCardFormModal` + `CreditCardFormViewModel`
- `RecurringFormModal` + `RecurringFormViewModel`
- `AddTransactionModal` / `EditTransactionModal` + respective ViewModels

## Standard Form Contract

All non-trivial forms follow this structure:

1. `XxxFormAction` sealed class with a single public intent entrypoint: `onAction(action)`.
2. `XxxFormUiState` data class with all reactive data required by UI.
3. `XxxFormViewModel` orchestrating:
- reactive data sources (`combine`, `stateIn`)
- user selections and derived values
- submit orchestration through use cases/repositories

Keep business decisions out of composables.

## State Ownership

### ViewModel state

Use `MutableStateFlow` for durable form state:
- selected entities (`account`, `creditCard`, `invoiceMonth`, etc.)
- edit/create mode flags
- validation maps and derived `canSubmit`

Expose a single immutable `uiState`.

### Composable-local state

Use local UI state for text input ergonomics:
- `rememberTextFieldState(...)`
- local toggles (`type`, `target`) when purely presentation-driven

Bridge local input changes to ViewModel using one of the two supported approaches:

1. `snapshotFlow { textState.text.toString() }` -> `onAction(...)` (used in forms with debounce validation).
2. Direct `onValueChange` -> `onAction(...)` (used when debouncing is not needed).

Never store `TextFieldState` inside ViewModel.

## Domain Form Objects

When form rules are reusable across create/edit flows, use domain form objects:
- `TransactionForm`
- `RecurringForm`
- `CreditCardForm`

Rules:
- keep normalization in `from(...)` factories (target coercion, category compatibility, nullability shaping).
- keep fast guard validation in `isValid()` for submit enablement.
- keep authoritative build/validation in `build()` or use case (`Either` / `ensure`).

UI can use `form.isValid()` for button enablement, but submit path must still validate again in domain/use case.

## Monetary and Date Fields (Required Pattern)

### Monetary fields

Use this pipeline:

1. UI input masking with `rememberMoneyInputTransformation()`.
2. Keep value as `String` in form state.
3. Parse with `moneyToDouble()` only when validating/submitting.
4. Revalidate amount constraints in use case/domain (`> 0`, limits, debt caps, etc.).

Rules:
- Use shared parser `moneyToDouble()` (from `extension/MoneyFormatter.kt`).
- For read-only monetary fields, format from numeric value using `CurrencyFormatter` / `LocalCurrencyFormatter`.
- Avoid ad-hoc parse helpers duplicated inside modals.

### Date fields (`dd/MM/yyyy`)

Use this pipeline:

1. Initialize field with `dayMonthYear.format(initialDate)`.
2. Apply `DateInputTransformation()` to text fields.
3. Open `DatePickerModal` from trailing icon to select valid dates.
4. On selection, replace full text with `dayMonthYear.format(selectedDate)`.
5. Parse with `dayMonthYear.parse(...)` before submit.

For restricted periods:
- define `minDate` / `maxDate` in UI and pass to `DatePickerModal`.
- clamp initial value with `coerceIn(minDate, maxDate)` when needed.
- mirror the same bounds in domain/use case validation.

### Day-of-month fields (critical rule)

For fields like recurring `dayOfMonth`, card `closingDay`, and `dueDay`:
- use `DayInputTransformation()` in UI.
- accept and validate full range `1..31` in form/domain/use case.
- never cap to `28` just to avoid month-length complexity.

The app already handles shorter months at execution time:
- `YearMonth.safeOnDay(day)` clamps to the month last day.
- `YearMonth.effectiveDay(day)` is used in recurring/invoice logic.

This means input remains user-intent (`31` is valid), while scheduling/billing adapts per month safely.

## Validation Layers (Required)

### Layer 1: Interaction-level gating (UI)

- Disable submit button using `uiState.canSubmit` or `form.isValid()`.
- Show field loading/error states when async validation exists.

### Layer 2: Async field validation (ViewModel)

Use this for uniqueness/business checks while typing (name/title):
- `DebounceManager`
- `Validation.Validating | Validation.Valid | Validation.Error(UiText)`
- `validateXxxUseCase(...)` mapped to `Validation`

### Layer 3: Authoritative submit validation (Domain)

Always revalidate on submit:
- `form.build().bind()` or `useCase(...).bind()`
- return `Either`/exceptions mapped to domain errors

Never rely only on UI gating.

## Create vs Edit Flows

Forms usually support both modes in one implementation:
- constructor receives nullable domain model (`creditCard: CreditCard?`, `recurring: Recurring?`, etc.)
- `isEditMode = model != null`
- initial state seeded from existing model when editing
- submit branch:
  - create path for insert
  - edit path for update/copy

Dismiss behavior must reflect stack depth:
- `dismiss()` for create-only top modal
- `dismissAll()` when edit started from a parent detail modal or when parent becomes stale

## Submit Orchestration

Expected order:

1. Gather normalized form data.
2. Execute use case(s) in ViewModel (`viewModelScope.launch`).
3. Handle failure (`onLeft`) and success (`onRight`) explicitly.
4. On success, close modal stack with correct dismiss policy.

For expected one-shot UI effects during submit (snackbar, open dependent modal), use `Channel<Event>` + `receiveAsFlow()`.

## DI Pattern for Form ViewModels

Register in Koin with optional parameters for edit mode:

```kotlin
viewModel {
    CreditCardFormViewModel(
        creditCard = it.getOrNull(),
        ...
    )
}
```

Inside modal:

```kotlin
val viewModel = koinViewModel<CreditCardFormViewModel> { parametersOf(creditCard) }
```

## Anti-patterns

- Form logic inside composable `onClick` blocks (beyond dispatching `onAction`).
- Multiple public mutator methods in complex form ViewModels instead of `onAction`.
- UI-only validation without domain/use case revalidation on submit.
- Coupling `TextFieldState` to ViewModel.
- Mixing create and edit logic across separate duplicated ViewModels when one can handle both.
- Always using `dismissAll()` regardless of flow context.
- Parsing monetary/date values with custom local helpers when shared formatter/parser already exists.
- Enforcing date range only in UI without repeating constraints in use case/domain.
- Limiting day-of-month inputs to `1..28` instead of `1..31`.

## Checklist for New Forms

- Has `XxxFormAction`, `XxxFormUiState`, `XxxFormViewModel`.
- Complex interactions go through `onAction`.
- One `uiState` stream (`stateIn`) exposed.
- Local text input is bridged to ViewModel (`snapshotFlow` or `onValueChange`).
- Submit is gated by `canSubmit` or `form.isValid()`.
- Submit path revalidates in domain/use case.
- Create/edit mode is explicit and initialized from params.
- Dismiss strategy matches modal stack context.
- Money/date fields follow shared transformations and parsers (`rememberMoneyInputTransformation`, `DateInputTransformation`, `dayMonthYear`, `moneyToDouble`).
- Day-of-month fields explicitly allow `1..31` and rely on `safeOnDay/effectiveDay` for short months.
