# MVI + MVVM Pattern

## Purpose

This document defines the project standard for applying MVI + MVVM in ViewModels.
It is prescriptive.

## Core Cycle

UI sends intent -> ViewModel handles intent -> ViewModel updates `UiState` and emits one-shot `Event` -> UI renders state and reacts to events.

## Terminology

- `UiState`: persistent screen state (`StateFlow`).
- `Action`: user intent sent from UI to ViewModel.
- `Event`: one-shot effect emitted by ViewModel to UI (navigation, snackbar, share, print, open modal).

## ViewModel Types and `onAction`

### 1. Simple command ViewModel

Use this for single-command modals with no observable state.
Examples in current code: `DeleteAccountViewModel`, `CloseInvoiceViewModel`.

Rules:
- `onAction` is optional.
- `uiState` is optional.
- A single public command method is allowed (`delete()`, `confirm()`, etc.).

### 2. Complex ViewModel

Use this for forms, filters, selections, async loading, or multiple intents.
Examples in current code: `AddInstallmentViewModel`, `EditTransactionViewModel`, `ReportViewerViewModel`.

Rules:
- `onAction(action: XxxAction)` is required as the public intent entrypoint.
- Expose a single `uiState` stream.
- Internal mutations stay private.

## UiState Modeling

### Prefer sealed UiState for mutually exclusive states

Use `sealed class` or `sealed interface` when screen states are exclusive:
`Loading`, `Empty`, `Error`, `Content`.

This is the preferred model for async screens (as already used in:
`BudgetsUiState`, `CategoriesUiState`, `CreditCardsUiState`, `InstallmentsUiState`, `RecurringUiState`).

```kotlin
sealed class BudgetsUiState {
    abstract val selectedMonth: YearMonth

    data class Loading(override val selectedMonth: YearMonth) : BudgetsUiState()
    data class Empty(override val selectedMonth: YearMonth) : BudgetsUiState()
    data class Content(
        val budgetProgress: List<BudgetProgress>,
        override val selectedMonth: YearMonth,
    ) : BudgetsUiState()
}
```

### Use data class UiState for concurrent fields

Use `data class` when fields truly coexist, usually in forms and filter state.
Examples in current code: `AccountFormUiState`, `CategoryFormUiState`, `AddTransactionUiState`.

```kotlin
data class AccountFormUiState(
    val name: String,
    val selectedIcon: AppIcon,
    val validation: Map<AccountField, Validation>,
    val canSubmit: Boolean,
)
```

### Forbidden pattern

Do not model exclusive states with flag priority inside one object:
`isLoading + error + data`.

## One-shot Events

Use `Channel<Event>` with `receiveAsFlow()`:

```kotlin
private val _events = Channel<ReportViewerEvent>(Channel.BUFFERED)
val events = _events.receiveAsFlow()
```

Rules:
- Use `Event` for one-shot effects only.
- Do not use `MutableSharedFlow` for one-shot events.
- UI collects events in `LaunchedEffect`.

## Complex ViewModel Template

```kotlin
class SampleViewModel(
    private val repository: Repository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    private val _events = Channel<SampleEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState = combine(
        repository.observeItems(),
        query,
    ) { items, query ->
        val filtered = items.filter { it.matches(query) }
        when {
            filtered.isEmpty() -> SampleUiState.Empty(query)
            else -> SampleUiState.Content(filtered, query)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SampleUiState.Loading,
    )

    fun onAction(action: SampleAction) {
        when (action) {
            is SampleAction.QueryChanged -> query.value = action.value
            is SampleAction.ItemClicked -> viewModelScope.launch {
                _events.send(SampleEvent.NavigateToDetails(action.id))
            }
            SampleAction.Refresh -> refresh()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            repository.refresh()
        }
    }
}
```

## Composable Integration

Rules:
- Collect `uiState` as state (`collectAsStateWithLifecycle()` where lifecycle API is available).
- Collect `events` in `LaunchedEffect(viewModel)`.
- Send intents via `viewModel.onAction(...)` for complex ViewModels.

```kotlin
@Composable
fun SampleScreen(viewModel: SampleViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SampleEvent.NavigateToDetails -> { /* navigate */ }
            }
        }
    }

    SampleContent(
        uiState = uiState,
        onQueryChange = { viewModel.onAction(SampleAction.QueryChanged(it)) },
        onItemClick = { id -> viewModel.onAction(SampleAction.ItemClicked(id)) },
    )
}
```

## Coroutines and Initialization

Rules:
- Never use `runBlocking` in ViewModel.
- Prefer lightweight `initialValue` in `stateIn`.
- Use `viewModelScope.launch` for async startup work.
- Keep long-running state reactive (`combine`, `map`, `flatMapLatest`, `stateIn`).

## Anti-patterns

- Complex ViewModel exposing many public mutators (`onNameChanged`, `onFilterChanged`, `onSubmit`) instead of `onAction`.
- Exclusive async states modeled with flag priority in one data class.
- One-shot effects in `MutableSharedFlow`.
- Exposing `MutableStateFlow` publicly.
- Android/Compose-specific dependencies inside domain layer code.
