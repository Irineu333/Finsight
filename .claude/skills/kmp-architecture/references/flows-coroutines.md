# Flows & Coroutines

## StateFlow vs SharedFlow vs Channel

| Type | Replay | Use case |
|------|--------|----------|
| `StateFlow<T>` | Last value (always 1) | UI state — screen always has a current value |
| `SharedFlow<T>` | Configurable (0–N) | Multiple collectors, optional history |
| `Channel<T>` | None | One-shot events (navigation, toast) — one collector |

**StateFlow for UI state:**
```kotlin
private val _uiState = MutableStateFlow(MyUiState())
val uiState: StateFlow<MyUiState> = _uiState.asStateFlow()
```

**Channel for actions:**
```kotlin
private val _action = Channel<MyAction>()
val action: Flow<MyAction> = _action.receiveAsFlow()
```

Never use `SharedFlow` for one-shot navigation events — it can replay to new collectors
(e.g., after screen rotation), causing duplicate navigation.

## Coroutine Scopes

| Scope | Lifetime | Use |
|-------|----------|-----|
| `viewModelScope` | Until ViewModel cleared | All ViewModel coroutines |
| `lifecycleScope` | Until Activity/Fragment destroyed | UI-bound work |
| `rememberCoroutineScope()` | Until composable leaves composition | Composable-triggered coroutines |
| Injected `CoroutineScope` | App lifetime or custom | Repository background work |

**Never use `GlobalScope`** — it has no structured concurrency, leaks indefinitely.

```kotlin
// CORRECT
class MyViewModel(
    private val useCase: MyUseCase
) : ViewModel() {
    init {
        viewModelScope.launch { /* tied to ViewModel lifecycle */ }
    }
}

// WRONG
class MyViewModel : ViewModel() {
    init {
        GlobalScope.launch { /* ❌ never cancelled, leaks */ }
    }
}
```

## Dispatchers

| Dispatcher | Use |
|------------|-----|
| `Dispatchers.Main` | UI updates (default for `viewModelScope`) |
| `Dispatchers.IO` | Database, file, network I/O |
| `Dispatchers.Default` | CPU-intensive work (sorting, parsing) |
| `Dispatchers.Unconfined` | Only in tests |

```kotlin
// Repository implementation
override suspend fun findAll(): List<Account> = withContext(Dispatchers.IO) {
    dao.findAll().map { it.toDomain() }
}
```

Inject the dispatcher for testability:
```kotlin
class MyRepositoryImpl(
    private val dao: MyDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
)
```

## Flow Operators — Key Patterns

### Combining state from multiple sources
```kotlin
combine(
    repository.observeAccounts(),
    repository.observeTransactions(),
) { accounts, transactions ->
    accounts.map { account ->
        account.copy(transactionCount = transactions.count { it.accountId == account.id })
    }
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
```

### Converting Flow to StateFlow in ViewModel
```kotlin
val accounts: StateFlow<List<Account>> = repository.observeAll()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000), // stop 5s after last subscriber
        initialValue = emptyList()
    )
```

**`WhileSubscribed(5_000)`** is the Google-recommended default — keeps the flow alive for 5 seconds
after the UI goes to background (handles config changes without re-fetching).

### Filtering and transforming
```kotlin
repository.observeTransactions()
    .map { it.filter { tx -> tx.amount > 0 } }
    .distinctUntilChanged()
    .collect { ... }
```

### Error handling in flows
```kotlin
repository.observeTransactions()
    .catch { e ->
        _uiState.update { it.copy(error = UiText.DynamicString(e.message ?: "Unknown error")) }
        emit(emptyList()) // emit fallback to keep flow alive
    }
    .collect { transactions ->
        _uiState.update { it.copy(transactions = transactions) }
    }
```

Never let an unhandled exception in `.collect {}` crash the ViewModel silently.

### Debounce for search inputs
```kotlin
private val searchQuery = MutableStateFlow("")

val searchResults: StateFlow<List<Transaction>> = searchQuery
    .debounce(300)
    .distinctUntilChanged()
    .flatMapLatest { query -> repository.search(query) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

fun onSearchQueryChange(query: String) {
    searchQuery.value = query
}
```

`flatMapLatest` cancels the previous search when a new query arrives.

## Structured Concurrency

Launch multiple concurrent operations and wait for all:
```kotlin
viewModelScope.launch {
    val accounts = async { accountRepository.findAll() }
    val categories = async { categoryRepository.findAll() }
    // Both run in parallel, both must complete
    _uiState.update { it.copy(accounts = accounts.await(), categories = categories.await()) }
}
```

Launch fire-and-forget with error handling:
```kotlin
viewModelScope.launch {
    runCatching { deleteUseCase(id) }
        .onFailure { e -> _uiState.update { it.copy(error = UiText.from(e)) } }
}
```

## Flow Cold vs Hot

| Cold Flow | Hot Flow |
|-----------|----------|
| Starts on each `collect {}` | Runs independently of collectors |
| Room `@Query` returning `Flow<T>` | `StateFlow`, `SharedFlow` |
| `flow { emit(...) }` builder | `MutableStateFlow`, `Channel` |

Room DAOs return **cold flows** — each `collect` re-subscribes to the database.
Converting to `StateFlow` with `stateIn()` makes it hot (shared across collectors).

## Anti-patterns

```kotlin
// ❌ Blocking the main thread
viewModelScope.launch(Dispatchers.Main) {
    val result = dao.findAll() // ❌ database on Main thread
}

// ❌ Not using distinctUntilChanged on expensive transforms
repository.observeAll()
    .map { expensiveTransform(it) } // re-runs on every emission, even identical values
    // ✅ add .distinctUntilChanged() after .map {}

// ❌ Collecting in init without lifecycle awareness
init {
    viewModelScope.launch {
        uiState.collect { ... } // ❌ collecting your own state
    }
}

// ❌ Using flow.value outside coroutine
val current = _uiState.value // ✅ this is fine for StateFlow
// But never:
someSharedFlow.value // ❌ SharedFlow has no .value
```