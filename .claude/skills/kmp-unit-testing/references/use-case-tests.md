# Use Case Tests

## Structure

Use cases are pure: they receive dependencies via constructor, run logic, return a result.
Tests inject fakes and assert on the returned `Either`.

```kotlin
class CreateTransactionUseCaseTest {

    // Arrange — shared setup
    private val fakeRepository = FakeTransactionRepository()
    private val useCase = CreateTransactionUseCase(fakeRepository)

    @Test
    fun `given valid transaction when saved then returns success`() = runTest {
        val transaction = buildTransaction(amount = 50.0)

        val result = useCase(transaction)

        assertTrue(result.isRight())
        assertEquals(1, fakeRepository.saved.size)
    }

    @Test
    fun `given negative amount when saved then returns InvalidAmount error`() = runTest {
        val transaction = buildTransaction(amount = -10.0)

        val result = useCase(transaction)

        assertEquals(TransactionError.InvalidAmount, result.leftOrNull())
    }

    @Test
    fun `given zero amount when saved then returns InvalidAmount error`() = runTest {
        val transaction = buildTransaction(amount = 0.0)

        val result = useCase(transaction)

        assertEquals(TransactionError.InvalidAmount, result.leftOrNull())
    }

    @Test
    fun `given repository failure when saved then propagates error`() = runTest {
        fakeRepository.shouldFailOnSave = true
        val transaction = buildTransaction(amount = 50.0)

        val result = useCase(transaction)

        assertTrue(result.isLeft())
    }
}
```

## Test Builders

Avoid constructing full domain objects inline — use builders for readability and maintainability.

```kotlin
// In commonTest — shared across all test files
fun buildTransaction(
    id: Long = 0L,
    amount: Double = 100.0,
    description: String = "Test",
    accountId: Long = 1L,
    date: LocalDate = LocalDate(2024, 1, 1),
    type: TransactionType = TransactionType.EXPENSE,
) = Transaction(
    id = id,
    amount = amount,
    description = description,
    accountId = accountId,
    date = date,
    type = type,
)

fun buildAccount(
    id: Long = 1L,
    name: String = "Wallet",
    balance: Double = 0.0,
    currency: String = "BRL",
) = Account(id = id, name = name, balance = balance, currency = currency)
```

## Validation Use Cases

Validation use cases return `Either` directly — no exceptions, no suspend.

```kotlin
class ValidateTransactionAmountUseCaseTest {

    private val useCase = ValidateTransactionAmountUseCase()

    @Test
    fun `given positive amount then returns Right`() {
        val result = useCase(100.0)
        assertTrue(result.isRight())
    }

    @Test
    fun `given zero then returns InvalidAmount`() {
        val result = useCase(0.0)
        assertEquals(TransactionError.InvalidAmount, result.leftOrNull())
    }

    @Test
    fun `given negative then returns InvalidAmount`() {
        val result = useCase(-1.0)
        assertEquals(TransactionError.InvalidAmount, result.leftOrNull())
    }
}
```

## Operation Use Cases (suspend, Either)

Operation use cases suspend and may call multiple repositories.

```kotlin
class TransferBetweenAccountsUseCaseTest {

    private val fakeAccountRepository = FakeAccountRepository()
    private val fakeTransactionRepository = FakeTransactionRepository()
    private val useCase = TransferBetweenAccountsUseCase(
        accountRepository = fakeAccountRepository,
        transactionRepository = fakeTransactionRepository,
    )

    @Test
    fun `given sufficient balance when transfer then both accounts updated`() = runTest {
        val source = buildAccount(id = 1L, balance = 200.0)
        val destination = buildAccount(id = 2L, balance = 50.0)
        fakeAccountRepository.emit(listOf(source, destination))

        val result = useCase(
            sourceId = 1L,
            destinationId = 2L,
            amount = 100.0,
        )

        assertTrue(result.isRight())
        assertEquals(100.0, fakeAccountRepository.accounts.first { it.id == 1L }.balance)
        assertEquals(150.0, fakeAccountRepository.accounts.first { it.id == 2L }.balance)
        assertEquals(2, fakeTransactionRepository.saved.size)
    }

    @Test
    fun `given insufficient balance when transfer then returns InsufficientFunds`() = runTest {
        val source = buildAccount(id = 1L, balance = 10.0)
        val destination = buildAccount(id = 2L, balance = 0.0)
        fakeAccountRepository.emit(listOf(source, destination))

        val result = useCase(sourceId = 1L, destinationId = 2L, amount = 100.0)

        assertEquals(TransferError.InsufficientFunds, result.leftOrNull())
        assertTrue(fakeTransactionRepository.saved.isEmpty()) // no side effects on failure
    }

    @Test
    fun `given nonexistent source account when transfer then returns AccountNotFound`() = runTest {
        fakeAccountRepository.emit(emptyList())

        val result = useCase(sourceId = 99L, destinationId = 2L, amount = 100.0)

        assertEquals(TransferError.AccountNotFound, result.leftOrNull())
    }
}
```

## Coverage Checklist

For every use case, write tests for:
- [ ] Happy path (correct input → expected output + side effects)
- [ ] Every validation error (each guard clause)
- [ ] Repository failure propagation
- [ ] Edge cases (zero, empty, boundaries)
- [ ] No side effects on failure (nothing saved when error occurs)