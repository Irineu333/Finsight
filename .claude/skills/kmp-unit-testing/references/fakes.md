# Fake Repositories

## Why Fakes, Not Mocks

Mocks verify *interactions* (which methods were called). Fakes verify *behavior* (what the
system produces). Fakes survive refactors; mocks break when internals change.

```kotlin
// BAD — mock
val repo = mock(AccountRepository::class.java)
whenever(repo.findById(1L)).thenReturn(account)

// GOOD — fake
val repo = FakeAccountRepository()
repo.emit(listOf(account))
```

## Standard Fake Pattern

Implement the domain interface with `MutableStateFlow` for observable state.
Expose test helpers as public properties.

```kotlin
class FakeAccountRepository : AccountRepository {

    // Backing state — tests can prime or inspect it
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())

    // Expose for test assertions
    val accounts: List<Account> get() = _accounts.value

    // Error simulation flags
    var shouldFailOnSave: Boolean = false
    var shouldFailOnDelete: Boolean = false

    // Test helper to emit new state
    fun emit(accounts: List<Account>) {
        _accounts.value = accounts
    }

    // --- Interface implementation ---

    override fun observeAll(): Flow<List<Account>> = _accounts.asStateFlow()

    override suspend fun findById(id: Long): Account? =
        _accounts.value.find { it.id == id }

    override suspend fun save(account: Account): Either<AccountError, Unit> {
        if (shouldFailOnSave) return AccountError.SaveFailed.left()
        _accounts.update { current ->
            current.filterNot { it.id == account.id } + account
        }
        return Unit.right()
    }

    override suspend fun delete(id: Long): Either<AccountError, Unit> {
        if (shouldFailOnDelete) return AccountError.NotFound.left()
        _accounts.update { it.filterNot { a -> a.id == id } }
        return Unit.right()
    }
}
```

## Fake for Write-Only Repositories

When the repository has no observable state, expose a captured list.

```kotlin
class FakeTransactionRepository : TransactionRepository {

    val saved = mutableListOf<Transaction>()
    val deleted = mutableListOf<Long>()
    var shouldFailOnSave: Boolean = false

    override suspend fun save(transaction: Transaction): Either<TransactionError, Unit> {
        if (shouldFailOnSave) return TransactionError.InvalidAmount.left()
        saved.add(transaction)
        return Unit.right()
    }

    override suspend fun delete(id: Long): Either<TransactionError, Unit> {
        deleted.add(id)
        return Unit.right()
    }

    override fun observeByAccount(accountId: Long): Flow<List<Transaction>> =
        flowOf(saved.filter { it.accountId == accountId })
}
```

## Fake with Configurable Responses

For complex scenarios, use a response queue.

```kotlin
class FakeRemoteDataSource : RemoteDataSource {

    private val responses: ArrayDeque<Either<NetworkError, SyncResult>> = ArrayDeque()

    fun enqueue(response: Either<NetworkError, SyncResult>) {
        responses.addLast(response)
    }

    override suspend fun sync(): Either<NetworkError, SyncResult> =
        responses.removeFirstOrNull() ?: NetworkError.Unavailable.left()
}
```

## Naming Convention

| Suffix | When to use |
|--------|-------------|
| `FakeXxxRepository` | Implements a repository interface |
| `FakeXxxDataSource` | Implements a raw data source |
| `FakeXxxService` | Implements an external service interface |

Always place fakes in `src/commonTest/` or `src/test/` alongside the tests that use them.
Shared fakes used across multiple test classes go in a `fakes/` subpackage.