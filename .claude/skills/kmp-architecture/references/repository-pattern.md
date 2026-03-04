# Repository Pattern

## Interface (domain layer)

Repository interfaces describe the app's data needs in domain language.
They live in `/domain/repository/` and depend only on domain models.

```kotlin
interface AccountRepository {
    fun observeAll(): Flow<List<Account>>
    suspend fun findById(id: Long): Account?
    suspend fun save(account: Account): Either<AccountError, Unit>
    suspend fun delete(id: Long): Either<AccountError, Unit>
}
```

**Rules for interfaces:**
- Return domain models, never entities
- Use `Flow<T>` for reactive queries (Room will push updates automatically)
- Use `suspend` for one-shot reads/writes
- Return `Either<Error, Value>` for operations that can fail with known errors
- Return nullable (`T?`) for single-item lookups that may not exist

## Implementation (database layer)

```kotlin
class AccountRepositoryImpl(
    private val dao: AccountDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : AccountRepository {

    override fun observeAll(): Flow<List<Account>> =
        dao.observeAll().map { entities -> entities.map(AccountEntity::toDomain) }

    override suspend fun findById(id: Long): Account? = withContext(dispatcher) {
        dao.findById(id)?.toDomain()
    }

    override suspend fun save(account: Account): Either<AccountError, Unit> =
        Either.catch {
            withContext(dispatcher) { dao.upsert(account.toEntity()) }
        }.mapLeft { AccountError.SaveFailed }

    override suspend fun delete(id: Long): Either<AccountError, Unit> =
        Either.catch {
            withContext(dispatcher) { dao.deleteById(id) }
        }.mapLeft { AccountError.DeleteFailed }
}
```

## Mappers

Mappers are extension functions, not classes. They live in `/database/mapper/` or
alongside the entity file.

```kotlin
// AccountEntity.kt or AccountMapper.kt
fun AccountEntity.toDomain(): Account = Account(
    id = id,
    name = name,
    balance = balance,
    type = AccountType.valueOf(type),
)

fun Account.toEntity(): AccountEntity = AccountEntity(
    id = id,
    name = name,
    balance = balance,
    type = type.name,
)
```

**Mapper rules:**
- One function per direction: `Entity.toDomain()` and `Domain.toEntity()`
- No business logic in mappers — just field mapping
- Handle nullable fields explicitly; don't use `!!`

## Dispatcher Injection

Inject `CoroutineDispatcher` rather than hardcoding `Dispatchers.IO` — makes testing easier.

```kotlin
// In Koin module
single<CoroutineDispatcher> { Dispatchers.IO }

class AccountRepositoryImpl(
    private val dao: AccountDao,
    private val dispatcher: CoroutineDispatcher // injected
) : AccountRepository
```

In tests, inject `UnconfinedTestDispatcher` or `StandardTestDispatcher`.

## Flow vs Suspend

| Use | When |
|-----|------|
| `Flow<T>` | Data that changes over time (list of records, balance) |
| `suspend fun T?` | One-shot read that may return nothing |
| `suspend fun Either<E, Unit>` | Write/delete that can fail |
| `suspend fun Either<E, T>` | One-shot read that can fail for a known reason |

## DI Registration

```kotlin
val databaseModule = module {
    single<AccountRepository> { AccountRepositoryImpl(get(), get()) }
    single<TransactionRepository> { TransactionRepositoryImpl(get(), get()) }
}
```

Repositories are `single {}` — one instance shared across the app.

## Anti-patterns

```kotlin
// ❌ Returning entity from repository
interface AccountRepository {
    fun observeAll(): Flow<List<AccountEntity>> // ❌ entity in domain interface
}

// ❌ Business logic in repository
class AccountRepositoryImpl : AccountRepository {
    override suspend fun save(account: Account) {
        if (account.balance < 0) throw ... // ❌ validation belongs in UseCase
    }
}

// ❌ Repository calling another repository
class TransactionRepositoryImpl(
    private val accountRepository: AccountRepository // ❌ cross-repo dependency
)
```