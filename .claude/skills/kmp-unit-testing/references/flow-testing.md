# Flow Testing with Turbine

## Why Turbine

`Flow.toList()` blocks forever on infinite flows. `Flow.first()` cancels the flow after one
emission and can miss timing issues. Turbine provides a sequential, readable API for asserting
on flow emissions one at a time.

```
dependencies {
    testImplementation("app.cash.turbine:turbine:1.x.x")
}
```

## Basic Usage

```kotlin
@Test
fun `emits accounts list`() = runTest {
    fakeRepository.emit(listOf(buildAccount()))

    fakeRepository.observeAll().test {
        val accounts = awaitItem()
        assertEquals(1, accounts.size)
        awaitComplete() // only for finite flows
    }
}
```

## Asserting Multiple Emissions in Order

```kotlin
@Test
fun `emits loading then data`() = runTest {
    viewModel.uiState.test {
        val loading = awaitItem()
        assertTrue(loading.isLoading)

        fakeRepository.emit(listOf(buildAccount()))

        val loaded = awaitItem()
        assertFalse(loaded.isLoading)
        assertEquals(1, loaded.accounts.size)
    }
}
```

## Skipping Emissions You Don't Care About

```kotlin
@Test
fun `after delete emits updated list`() = runTest {
    val accounts = listOf(buildAccount(id = 1L), buildAccount(id = 2L))
    fakeRepository.emit(accounts)

    viewModel.uiState.test {
        skipItems(1) // skip initial load

        viewModel.onDeleteConfirmed(1L)

        val updated = awaitItem()
        assertEquals(1, updated.accounts.size)
    }
}
```

## Testing Channels (Actions)

`Channel<Action>` exposed as `Flow<Action>` behaves like a hot flow. Test it the same way.

```kotlin
@Test
fun `emits NavigateBack after save`() = runTest {
    viewModel.action.test {
        viewModel.onSave()
        assertEquals(MyAction.NavigateBack, awaitItem())
    }
}
```

## Testing StateFlow Initial Value

`StateFlow` always has an initial value. The first `awaitItem()` returns the current value
at the time `.test {}` is called.

```kotlin
@Test
fun `initial uiState has empty list`() = runTest {
    viewModel.uiState.test {
        val initial = awaitItem()
        assertTrue(initial.accounts.isEmpty())
        cancelAndIgnoreRemainingEvents()
    }
}
```

## Expecting No Further Events

```kotlin
@Test
fun `no action emitted when input invalid`() = runTest {
    viewModel.action.test {
        viewModel.onSaveWithInvalidInput()
        expectNoEvents()
    }
}
```

## Error Events

```kotlin
@Test
fun `flow emits error on repository exception`() = runTest {
    fakeRepository.shouldThrowOnObserve = true

    fakeRepository.observeAll().test {
        awaitError() // asserts the flow terminated with an exception
    }
}
```

## Turbine in Plain Flow Tests (non-ViewModel)

```kotlin
@Test
fun `repository emits new list after save`() = runTest {
    repository.observeAll().test {
        assertEquals(emptyList(), awaitItem())

        repository.save(buildAccount(name = "Wallet"))

        val updated = awaitItem()
        assertEquals(1, updated.size)
        assertEquals("Wallet", updated.first().name)
    }
}
```

## Anti-Patterns

```kotlin
// BAD — toList() hangs on infinite flows (StateFlow never completes)
val items = flow.toList()

// BAD — first() cancels after one emission, misses subsequent state
val item = flow.first()

// BAD — manual collection in a separate coroutine is fragile
val results = mutableListOf<T>()
val job = launch { flow.collect { results.add(it) } }
delay(100)
job.cancel()

// GOOD — Turbine
flow.test {
    val item = awaitItem()
    // assert on item
}
```