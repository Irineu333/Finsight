# Clean Architecture for KMP

## Layer Map

```
composeApp/src/commonMain/kotlin/
├── domain/
│   ├── repository/        # interfaces only — no implementations
│   ├── usecase/           # one class per use case
│   └── error/             # error enums/sealed classes
├── database/              # Room: entities, DAOs, mappers, repo implementations
└── ui/
    ├── screen/            # Screen composables + ViewModel + UiState
    ├── modal/             # ModalBottomSheet subclasses
    └── component/         # reusable composables
```

## Dependency Rule

```
ui  ──depends on──▶  domain  ◀──depends on──  database
                       │
                  (no imports from ui or database)
```

**Enforcement checklist:**
- `domain/` imports: only Kotlin stdlib, Arrow, kotlinx-coroutines
- `database/` imports: domain + Room + SQLite drivers
- `ui/` imports: domain + Compose + ViewModel + Koin

## What Belongs Where

### `/domain/repository/`
Pure Kotlin interfaces. Describe *what* the app needs, not *how* it's stored.

```kotlin
// CORRECT
interface TransactionRepository {
    fun observeByAccount(accountId: Long): Flow<List<Transaction>>
    suspend fun save(transaction: Transaction): Either<TransactionError, Unit>
}

// WRONG — Room leaks into domain
interface TransactionRepository {
    fun observeByAccount(accountId: Long): Flow<List<TransactionEntity>> // ❌
}
```

### `/domain/usecase/`
Single-responsibility classes. One public operator fun. No state.

```kotlin
class GetTransactionsByAccountUseCase(
    private val repository: TransactionRepository
) {
    operator fun invoke(accountId: Long): Flow<List<Transaction>> =
        repository.observeByAccount(accountId)
}
```

### `/domain/error/`
See `error-handling.md`.

### `/database/`
- **Entities**: Room `@Entity` classes — never exposed to UI or domain
- **DAOs**: `@Dao` interfaces
- **Mappers**: `TransactionEntity.toDomain()` / `Transaction.toEntity()` extension functions
- **Repository implementations**: implement domain interfaces, map entities ↔ domain models

```kotlin
class TransactionRepositoryImpl(
    private val dao: TransactionDao
) : TransactionRepository {
    override fun observeByAccount(accountId: Long): Flow<List<Transaction>> =
        dao.observeByAccount(accountId).map { it.map(TransactionEntity::toDomain) }
}
```

### `/ui/screen/FeatureName/`
Typical structure per screen:

```
ui/screen/Dashboard/
├── DashboardScreen.kt      # @Composable, collects uiState, dispatches events
├── DashboardViewModel.kt   # StateFlow<UiState>, Channel<Action>
└── DashboardUiState.kt     # data class UiState + sealed class Action
```

## Domain Model vs Entity vs UI Model

| Concern | Layer | Type |
|---------|-------|------|
| Persistence schema | database | `@Entity` data class |
| Business rules | domain | plain `data class` / `value class` |
| Display formatting | ui | can be domain model + extension, or dedicated UI model |

Avoid creating a separate UI model unless the screen needs data from multiple domain models merged,
or requires display-only computed properties that would pollute the domain model.