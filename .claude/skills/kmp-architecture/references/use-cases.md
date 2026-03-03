# UseCase Design

## Two Types of Use Cases

### 1. Query / Validation UseCase
Returns data or validates input. Returns `Either<Error, Value>` directly (no suspend, or suspend).

```kotlin
// Returns a Flow (reactive query)
class GetTransactionsByAccountUseCase(
    private val repository: TransactionRepository
) {
    operator fun invoke(accountId: Long): Flow<List<Transaction>> =
        repository.observeByAccount(accountId)
}

// Validates and returns Either (no side effects)
class ValidateTransactionAmountUseCase {
    operator fun invoke(amount: String): Either<TransactionError, Double> {
        val parsed = amount.toDoubleOrNull()
            ?: return TransactionError.InvalidAmount.left()
        if (parsed <= 0) return TransactionError.NegativeAmount.left()
        return parsed.right()
    }
}
```

### 2. Operation UseCase
Performs a side effect (write, delete, transfer). Can throw — wraps error in `XxxException`
so it propagates correctly through coroutine boundaries.

```kotlin
class TransferBetweenAccountsUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
) {
    // throws TransferException on failure
    suspend operator fun invoke(params: TransferParams) {
        val source = accountRepository.findById(params.sourceId)
            ?: throw TransferException(TransferError.AccountNotFound)

        if (source.balance < params.amount)
            throw TransferException(TransferError.InsufficientBalance)

        transactionRepository.transfer(params)
            .getOrElse { throw TransferException(it) }
    }
}
```

**When to throw vs return Either:**

| Scenario | Approach |
|----------|----------|
| Validation — caller must handle the error explicitly | `Either<Error, Value>` |
| Operation where partial failure needs rollback context | `throw XxxException(error)` |
| Querying data that might not exist | `Either<Error, Value>` or nullable |
| Flows (reactive queries) | No error return — use `.catch {}` in ViewModel |

## Single Responsibility

Each use case does exactly **one thing**. If you find yourself passing flags to change behavior,
split it into two use cases.

```kotlin
// WRONG — flag-driven use case
class SaveTransactionUseCase(
    private val repository: TransactionRepository
) {
    suspend operator fun invoke(
        transaction: Transaction,
        isEdit: Boolean // ❌ split into two use cases
    )
}

// CORRECT
class CreateTransactionUseCase(...) { ... }
class UpdateTransactionUseCase(...) { ... }
```

## Composing Use Cases

Use cases can call other use cases. They cannot call ViewModels or composables.

```kotlin
class CreateRecurringTransactionUseCase(
    private val createTransaction: CreateTransactionUseCase,
    private val scheduleRecurrence: ScheduleRecurrenceUseCase,
) {
    suspend operator fun invoke(params: RecurringParams) {
        createTransaction(params.toTransaction())
            .flatMap { scheduleRecurrence(params.recurrence) }
            .getOrElse { throw RecurringTransactionException(it) }
    }
}
```

## Naming Convention

| Pattern | Example |
|---------|---------|
| `GetXxxUseCase` | `GetAccountByIdUseCase` |
| `ObserveXxxUseCase` | `ObserveTransactionsByMonthUseCase` |
| `CreateXxxUseCase` | `CreateTransactionUseCase` |
| `UpdateXxxUseCase` | `UpdateAccountUseCase` |
| `DeleteXxxUseCase` | `DeleteTransactionUseCase` |
| `ValidateXxxUseCase` | `ValidateTransferParamsUseCase` |
| `TransferXxxUseCase` / `XxxBetweenYyyUseCase` | `TransferBetweenAccountsUseCase` |

## DI Registration

```kotlin
// domain use cases — factory (new instance per injection)
val domainModule = module {
    factory { GetTransactionsByAccountUseCase(get()) }
    factory { ValidateTransactionAmountUseCase() }
    factory { CreateTransactionUseCase(get()) }
    factory { TransferBetweenAccountsUseCase(get(), get()) }
}
```

Never register use cases as `single {}` — they hold no state and should not be singletons.