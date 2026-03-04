# Coroutine Test Setup

## runTest vs runBlocking

Always use `runTest {}`. It automatically advances virtual time and works with `TestCoroutineScheduler`.
`runBlocking {}` has no virtual time and will hang on `delay()` calls.

```kotlin
// BAD
@Test
fun `saves data`() = runBlocking {
    val result = useCase()
    assertTrue(result.isRight())
}

// GOOD
@Test
fun `saves data`() = runTest {
    val result = useCase()
    assertTrue(result.isRight())
}
```

## UnconfinedTestDispatcher vs StandardTestDispatcher

| Dispatcher | Behavior | Use when |
|------------|----------|----------|
| `UnconfinedTestDispatcher` | Runs coroutines eagerly, inline | Most tests — simpler, less boilerplate |
| `StandardTestDispatcher` | Requires `advanceUntilIdle()` to run coroutines | When you need to control execution order explicitly |

### UnconfinedTestDispatcher (default for most tests)

Coroutines start immediately when launched. No need to call `advanceUntilIdle()`.

```kotlin
@Test
fun `loads accounts immediately`() = runTest(UnconfinedTestDispatcher()) {
    fakeRepository.emit(listOf(buildAccount()))
    // viewModel.init {} already ran — state is available
    assertTrue(viewModel.uiState.value.accounts.isNotEmpty())
}
```

### StandardTestDispatcher (when order matters)

```kotlin
@Test
fun `shows loading before data arrives`() = runTest {
    // Coroutines are queued, not started yet
    assertTrue(viewModel.uiState.value.isLoading) // initial state

    advanceUntilIdle() // now all coroutines run

    assertFalse(viewModel.uiState.value.isLoading)
}
```

### advanceTimeBy — for explicit delays

```kotlin
@Test
fun `debounce triggers after 300ms`() = runTest {
    viewModel.onSearchQueryChange("kotlin")
    advanceTimeBy(299)
    assertTrue(viewModel.uiState.value.results.isEmpty()) // too early

    advanceTimeBy(1) // 300ms total
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.results.isNotEmpty())
}
```

## MainDispatcherRule

Required for ViewModel tests. Replaces `Dispatchers.Main` with the test dispatcher.

```kotlin
// commonTest or androidUnitTest
class MainDispatcherRule : TestWatcher() {
    val dispatcher = UnconfinedTestDispatcher()
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
```

Usage:

```kotlin
class MyViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
}
```

## Injecting TestDispatcher into ViewModels

When a ViewModel or UseCase creates its own `CoroutineScope`, inject the dispatcher
so tests can control execution:

```kotlin
// Production
class SyncViewModel(
    private val syncUseCase: SyncUseCase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    fun sync() {
        viewModelScope.launch(dispatcher) { syncUseCase() }
    }
}

// Test
@Test
fun `sync updates state`() = runTest {
    val viewModel = SyncViewModel(
        syncUseCase = fakeSyncUseCase,
        dispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    viewModel.sync()

    assertTrue(viewModel.uiState.value.isSynced)
}
```

## TestScope and Background Work

When testing code that launches background coroutines, use the `TestScope` provided by `runTest`:

```kotlin
@Test
fun `background sync completes`() = runTest {
    val scope = backgroundScope // child scope that is cancelled after the test

    scope.launch { viewModel.startBackgroundSync() }

    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.syncComplete)
}
```

## KMP-Specific: No @get:Rule on commonTest

JUnit `@get:Rule` is Android/JVM only. For shared `commonTest` code:

```kotlin
// commonTest — use manual setup/teardown instead of @Rule
class MyUseCaseTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() = Dispatchers.setMain(testDispatcher)

    @AfterTest
    fun teardown() = Dispatchers.resetMain()
}
```

Or create a `commonTest` `BaseViewModelTest` that does this via `@BeforeTest`/`@AfterTest`
and have all ViewModel tests inherit from it.