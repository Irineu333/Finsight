# Testing Strategy

## Test Pyramid

```
        ╔══════════════╗
        ║   UI Tests   ║  (few — expensive, slow)
        ╠══════════════╣
        ║ Integration  ║  (some — repository + Room in-memory)
        ╠══════════════╣
        ║  Unit Tests  ║  (many — use cases, ViewModels, mappers)
        ╚══════════════╝
```

## Unit Testing Use Cases

Use cases are pure — just inject fake repositories.

```kotlin
class CreateTransactionUseCaseTest {

    private val fakeRepository = FakeTransactionRepository()
    private val useCase = CreateTransactionUseCase(fakeRepository)

    @Test
    fun `saves valid transaction successfully`() = runTest {
        val transaction = Transaction(amount = 50.0, description = "Coffee")

        val result = useCase(transaction)

        assertTrue(result.isRight())
        assertEquals(1, fakeRepository.savedTransactions.size)
    }

    @Test
    fun `returns error for negative amount`() = runTest {
        val transaction = Transaction(amount = -10.0, description = "Coffee")

        val result = useCase(transaction)

        assertTrue(result.isLeft())
        assertEquals(TransactionError.InvalidAmount, result.leftOrNull())
    }
}
```

## Fake Repositories

Implement the domain interface with in-memory state. Never mock repositories — fakes are
more maintainable and closer to real behavior.

```kotlin
class FakeAccountRepository : AccountRepository {

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val savedAccounts: List<Account> get() = _accounts.value

    override fun observeAll(): Flow<List<Account>> = _accounts.asStateFlow()

    override suspend fun findById(id: Long): Account? =
        _accounts.value.find { it.id == id }

    override suspend fun save(account: Account): Either<AccountError, Unit> {
        _accounts.update { current ->
            current.filterNot { it.id == account.id } + account
        }
        return Unit.right()
    }

    override suspend fun delete(id: Long): Either<AccountError, Unit> {
        _accounts.update { it.filterNot { a -> a.id == id } }
        return Unit.right()
    }

    // Test helper to simulate errors
    var shouldFailOnSave = false
    // override save to check flag...
}
```

## Unit Testing ViewModels

```kotlin
class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule() // sets Dispatchers.Main to test dispatcher

    private val fakeRepository = FakeTransactionRepository()
    private val getTransactions = GetTransactionsByAccountUseCase(fakeRepository)
    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setup() {
        viewModel = DashboardViewModel(getTransactions)
    }

    @Test
    fun `initial state is loading`() {
        assertEquals(true, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `loads transactions on init`() = runTest {
        fakeRepository.emit(listOf(Transaction(id = 1, amount = 100.0)))

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals(1, state.transactions.size)
        }
    }

    @Test
    fun `emits navigate action on transaction click`() = runTest {
        viewModel.action.test {
            viewModel.onTransactionClick(42L)
            val action = awaitItem()
            assertEquals(DashboardAction.NavigateToTransaction(42L), action)
        }
    }
}
```

## MainDispatcherRule

Required to set `Dispatchers.Main` in unit tests (not available outside Android runtime).

```kotlin
class MainDispatcherRule(
    private val dispatcher: TestCoroutineDispatcher = TestCoroutineDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }
    override fun finished(description: Description) {
        Dispatchers.resetMain()
        dispatcher.cleanupTestCoroutines()
    }
}
```

Or with the newer API:
```kotlin
class MainDispatcherRule : TestWatcher() {
    val dispatcher = UnconfinedTestDispatcher()
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
```

## Testing Flows with Turbine

[Turbine](https://github.com/cashapp/turbine) is the recommended library for testing flows.

```kotlin
@Test
fun `emits updated list when item deleted`() = runTest {
    fakeRepository.emit(listOf(account1, account2))

    viewModel.uiState.test {
        awaitItem() // initial emission

        viewModel.onDelete(account1.id)

        val updated = awaitItem()
        assertEquals(1, updated.accounts.size)
        assertFalse(updated.accounts.contains(account1.toUi()))
    }
}
```

## Integration Testing with Room

Use Room's in-memory database for repository integration tests.

```kotlin
class AccountRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: AccountRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        repository = AccountRepositoryImpl(db.accountDao(), UnconfinedTestDispatcher())
    }

    @After
    fun teardown() = db.close()

    @Test
    fun `observeAll emits updated list after save`() = runTest {
        repository.observeAll().test {
            assertEquals(emptyList(), awaitItem())

            repository.save(Account(name = "Wallet", balance = 100.0))
            val accounts = awaitItem()
            assertEquals(1, accounts.size)
            assertEquals("Wallet", accounts.first().name)
        }
    }
}
```

## What to Test

| Component | What to test |
|-----------|-------------|
| Use cases | All business rule paths (happy path + all error cases) |
| ViewModels | State transitions, action emissions, error handling |
| Mappers | Entity ↔ domain round-trip (no data loss) |
| Repositories | Only in integration tests with in-memory Room |
| Composables | Rarely — prefer ViewModel + use case tests |

## What NOT to Mock

- Don't mock repositories — use fakes
- Don't mock use cases — use them with fake repositories
- Don't mock `Dispatchers` — use `MainDispatcherRule` and inject dispatchers