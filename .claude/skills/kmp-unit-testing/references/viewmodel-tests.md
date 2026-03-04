# ViewModel Tests

## Required Setup

Every ViewModel test file needs `MainDispatcherRule` to replace `Dispatchers.Main`
with a test dispatcher (unavailable in the JVM test runtime).

```kotlin
class MainDispatcherRule : TestWatcher() {
    val dispatcher = UnconfinedTestDispatcher()
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
```

Place this in `commonTest/` or `androidUnitTest/` and reuse across all ViewModel test files.

## Class Structure

```kotlin
class AccountListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Fakes
    private val fakeRepository = FakeAccountRepository()

    // Use cases (real, not mocked — they use the fake repo)
    private val getAccounts = GetAccountsUseCase(fakeRepository)
    private val deleteAccount = DeleteAccountUseCase(fakeRepository)

    // ViewModel created after setup so it uses initialized fakes
    private lateinit var viewModel: AccountListViewModel

    @Before
    fun setup() {
        viewModel = AccountListViewModel(getAccounts, deleteAccount)
    }
}
```

## Testing UiState — Initial State

```kotlin
@Test
fun `initial state has loading true and empty list`() {
    val state = viewModel.uiState.value

    assertTrue(state.isLoading)
    assertTrue(state.accounts.isEmpty())
}
```

## Testing UiState — After Data Loads

```kotlin
@Test
fun `given accounts exist when observed then state shows accounts`() = runTest {
    val account = buildAccount(id = 1L, name = "Wallet")
    fakeRepository.emit(listOf(account))

    viewModel.uiState.test {
        val state = awaitItem()
        assertFalse(state.isLoading)
        assertEquals(1, state.accounts.size)
        assertEquals("Wallet", state.accounts.first().name)
    }
}
```

## Testing UiState — Error State

```kotlin
@Test
fun `given repository fails when loading then state shows error`() = runTest {
    fakeRepository.shouldFailOnLoad = true

    viewModel.uiState.test {
        val state = awaitItem()
        assertFalse(state.isLoading)
        assertNotNull(state.error)
    }
}
```

## Testing Actions (one-shot events via Channel)

Actions are emitted once and must not replay. Test them with Turbine on `viewModel.action`.

```kotlin
@Test
fun `given account clicked when onAccountClick then emits NavigateToDetail`() = runTest {
    viewModel.action.test {
        viewModel.onAccountClick(42L)

        val action = awaitItem()
        assertEquals(AccountListAction.NavigateToDetail(42L), action)
    }
}

@Test
fun `given delete confirmed when onDelete then emits ShowDeleteSuccess`() = runTest {
    fakeRepository.emit(listOf(buildAccount(id = 1L)))

    viewModel.action.test {
        viewModel.onDeleteConfirmed(1L)

        val action = awaitItem()
        assertEquals(AccountListAction.ShowDeleteSuccess, action)
    }
}
```

## Testing State Sequences (multiple emissions)

Use Turbine to assert emissions in order.

```kotlin
@Test
fun `when delete runs then shows loading then hides it`() = runTest {
    fakeRepository.emit(listOf(buildAccount(id = 1L)))

    viewModel.uiState.test {
        val idle = awaitItem()
        assertFalse(idle.isLoading)

        viewModel.onDeleteConfirmed(1L)

        val loading = awaitItem()
        assertTrue(loading.isLoading)

        val done = awaitItem()
        assertFalse(done.isLoading)
    }
}
```

## Testing Form ViewModels (input + validation)

```kotlin
class CreateAccountViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeRepository = FakeAccountRepository()
    private val validateName = ValidateAccountNameUseCase()
    private val saveAccount = SaveAccountUseCase(fakeRepository)
    private val viewModel = CreateAccountViewModel(validateName, saveAccount)

    @Test
    fun `given empty name when submit then state shows name error`() = runTest {
        viewModel.onNameChange("")

        viewModel.uiState.test {
            viewModel.onSubmit()
            val state = awaitItem()
            assertNotNull(state.nameError)
        }
    }

    @Test
    fun `given valid form when submit then navigates away`() = runTest {
        viewModel.onNameChange("Savings")
        viewModel.onBalanceChange("1000.0")

        viewModel.action.test {
            viewModel.onSubmit()
            val action = awaitItem()
            assertEquals(CreateAccountAction.NavigateBack, action)
        }
    }
}
```

## Common Mistakes

| Mistake | Fix |
|---------|-----|
| `viewModel.uiState.value` inside `runTest` for async state | Use Turbine `.test { awaitItem() }` |
| Missing `@get:Rule val mainDispatcherRule` | All ViewModel tests need it |
| Mocking use cases | Use real use cases with fake repositories |
| `runBlocking {}` | Use `runTest {}` always |
| Testing `viewModel.action` without Turbine | Channel emissions are consumed once — Turbine is required |