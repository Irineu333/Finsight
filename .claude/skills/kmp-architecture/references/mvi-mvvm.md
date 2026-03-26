# MVI + MVVM Pattern

## Overview

The project uses a hybrid MVI/MVVM approach:
- **MVVM**: ViewModel holds state, UI observes
- **MVI**: unidirectional data flow — UI sends events → ViewModel processes → emits new UiState

```
UI (composable)
  │  collectAsStateWithLifecycle()
  ▼
ViewModel
  │  StateFlow<UiState>   ──▶  UI renders
  │  Channel<Action>      ──▶  UI handles one-shot events
  │
  ▼  calls
UseCase(s)
```

## UiState

Prefer explicit state modeling with **sealed UiState** when states are mutually exclusive
(e.g. `Loading`, `Empty`, `Error`, `Content`).

Use a `data class` for `UiState` when fields are truly concurrent (forms, filter controls, etc.).

```kotlin
// PREFERRED for async screens
sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data object Empty : DashboardUiState
    data class Error(val message: UiText) : DashboardUiState
    data class Content(
        val balance: String,
        val transactions: List<TransactionUi>,
    ) : DashboardUiState
}
```

Avoid priority conflicts like `isLoading + error + data` in one object for exclusive states.

## Actions (One-shot Events)

For navigation, toasts, dialogs, and other effects that **must not replay**,
use `Channel<Action>` exposed as `Flow<Action>`.

```kotlin
sealed class DashboardAction {
    data class NavigateToTransaction(val id: Long) : DashboardAction()
    data object ShowDeleteConfirmation : DashboardAction()
}

class DashboardViewModel : ViewModel() {
    private val _action = Channel<DashboardAction>()
    val action: Flow<DashboardAction> = _action.receiveAsFlow()

    fun onTransactionClick(id: Long) {
        viewModelScope.launch {
            _action.send(DashboardAction.NavigateToTransaction(id))
        }
    }
}
```

**Why Channel over SharedFlow?**
Channel delivers each item exactly once to one collector. SharedFlow replays to all collectors,
causing duplicate navigation on recomposition.

## ViewModel Structure

```kotlin
class DashboardViewModel(
    private val getTransactions: GetTransactionsByAccountUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _action = Channel<DashboardAction>()
    val action: Flow<DashboardAction> = _action.receiveAsFlow()

    init {
        observeTransactions()
    }

    private fun observeTransactions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            getTransactions()
                .catch { e -> _uiState.update { it.copy(isLoading = false, error = UiText.from(e)) } }
                .collect { transactions ->
                    _uiState.update {
                        it.copy(isLoading = false, transactions = transactions.toUiList())
                    }
                }
        }
    }

    fun onDeleteClick(id: Long) {
        viewModelScope.launch {
            deleteTransaction(id).fold(
                ifLeft = { error -> _uiState.update { it.copy(error = error.toUiText()) } },
                ifRight = { _action.send(DashboardAction.ShowDeleteConfirmation) }
            )
        }
    }
}
```

## Composable: Collecting State

```kotlin
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = koinViewModel(),
    onNavigateToTransaction: (Long) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // One-shot actions
    LaunchedEffect(Unit) {
        viewModel.action.collect { action ->
            when (action) {
                is DashboardAction.NavigateToTransaction -> onNavigateToTransaction(action.id)
                DashboardAction.ShowDeleteConfirmation -> { /* show dialog */ }
            }
        }
    }

    DashboardContent(
        uiState = uiState,
        onDeleteClick = viewModel::onDeleteClick,
    )
}
```

**Use `collectAsStateWithLifecycle()`** (not `collectAsState()`) — it respects Android
lifecycle and stops collection when the app is backgrounded, saving resources.

## Event Handling Patterns

| User action | How to handle |
|-------------|--------------|
| Button click | Direct `viewModel.onXxx()` function reference |
| Text input | `viewModel.onXxx(value)` on each keystroke, debounce in ViewModel if needed |
| Navigation trigger | Send `Action` from ViewModel, handle in Screen |
| Dialog confirmation | Send `Action` or update `UiState` flag |
| Pull-to-refresh | `viewModel.onRefresh()` → sets `isLoading = true`, re-fetches |

## Anti-patterns

```kotlin
// ❌ Business logic in composable
@Composable
fun Screen(viewModel: ViewModel) {
    val data by viewModel.data.collectAsState()
    val filtered = data.filter { it.amount > 0 } // ❌ belongs in ViewModel/UseCase
}

// ❌ ViewModel knows about Compose
class ViewModel : ViewModel() {
    fun onClick(context: Context) { /* ❌ no Android/Compose imports in ViewModel */ }
}

// ❌ Exposing mutable state
val _uiState = MutableStateFlow(UiState()) // ❌ should be private
val uiState = _uiState // ❌ should be .asStateFlow()
```
