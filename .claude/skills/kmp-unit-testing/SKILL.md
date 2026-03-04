---
name: kmp-unit-testing
description: >
  Unit testing expert for Kotlin Multiplatform projects. Always trigger when writing, reviewing,
  or designing tests for use cases, ViewModels, mappers, or repositories. Covers fake repositories,
  coroutine testing, Flow testing with Turbine, Arrow Either assertions, MainDispatcherRule,
  UiState transitions, Action emissions, and test naming conventions. Also trigger when the user
  asks how to test something, reports a flaky test, or needs help with runTest, TestDispatcher,
  or Turbine.
---

# KMP Unit Testing Expert Skill

State-of-the-art unit testing for Kotlin Multiplatform projects using Clean Architecture +
MVI/MVVM. Covers the full testing surface: use cases, ViewModels, mappers, and flows.

## Workflow

### 1. Identify what is being tested

| Subject | Reference |
|---------|-----------|
| Fake repositories and fake helpers | `references/fakes.md` |
| Use case tests (validation + operation) | `references/use-case-tests.md` |
| ViewModel tests (UiState + Actions) | `references/viewmodel-tests.md` |
| Flow and StateFlow testing with Turbine | `references/flow-testing.md` |
| Coroutine setup: dispatchers, runTest, TestScope | `references/coroutines-setup.md` |
| Assertions: Either, Turbine, state | `references/assertions.md` |

### 2. Apply the correct pattern

- Use cases → inject fakes, call directly, assert Either result
- ViewModels → `MainDispatcherRule` + Turbine on `uiState` and `action`
- Flows → always use Turbine `.test {}`, never `first()` or `toList()`
- Fakes → implement domain interface, expose `MutableStateFlow` for state
- Mappers → pure functions, assert round-trip with no data loss

### 3. Verify test quality

- [ ] Every business rule path has a test (happy + all errors)
- [ ] Test names describe behavior, not implementation (`given X when Y then Z`)
- [ ] No mocks for repositories or use cases — only fakes
- [ ] No `delay()` in tests — use `advanceUntilIdle()` or `UnconfinedTestDispatcher`
- [ ] Turbine `cancelAndConsumeRemainingEvents()` not needed when inside `runTest`
- [ ] `MainDispatcherRule` present in every ViewModel test class

### 4. Flag anti-patterns proactively

- `Mockito.mock(Repository::class)` → replace with Fake class
- `runBlocking {}` → replace with `runTest {}`
- `Thread.sleep()` → replace with `advanceTimeBy()` or `UnconfinedTestDispatcher`
- `flow.first()` in tests → replace with Turbine `.test { awaitItem() }`
- Missing error path tests → point out uncovered cases
- Testing implementation details (private methods, internal state) → test behavior instead

## Key Principles

1. **Fakes over mocks.** Fakes implement the real interface with in-memory state.
   They're readable, refactor-safe, and closer to production behavior.

2. **Test behavior, not implementation.** Assert on outputs (UiState, Either, emitted values),
   not on which methods were called or in what order.

3. **One test per business rule.** Each `@Test` covers one path through the logic.
   Multiple assertions in one test are acceptable when they describe one coherent behavior.

4. **Names describe intent.** Use backtick names: `given valid input when saved then returns success`.
   The test name is the documentation.

5. **Coroutine discipline.** Always `runTest {}`. Never `runBlocking {}`.
   Always `MainDispatcherRule` in ViewModel tests.

6. **Flow testing is Turbine.** Never collect flows with `toList()` or `first()` in tests.
   Turbine gives you deterministic, sequential assertions on emissions.