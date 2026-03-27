---
name: kmp-architecture
description: >
  Kotlin Multiplatform architecture expert. Guides Clean Architecture layering (domain/data/ui),
  MVI + MVVM patterns, UiState design, ViewModel lifecycle, UseCase structure, Repository pattern,
  structured concurrency, StateFlow, SharedFlow, Flow operators, coroutine scopes, Koin DI for KMP,
  Arrow error handling (Either/flatMap/catch), and testing strategies. Always trigger when creating,
  implementing, or designing any new feature, screen, use case, repository, or ViewModel. Also
  trigger when the user asks about architecture, layers, UiState, Flow, coroutines, Koin,
  dependency injection, error handling with Either, or how to structure KMP code.
user-invocable: false
---

# KMP Architecture Expert Skill

Practical, opinionated guidance for structuring Kotlin Multiplatform projects following
Clean Architecture + MVI/MVVM. Aligned with Google's Android architecture recommendations
and extended for multiplatform targets (Android, Desktop, iOS).

## Workflow

### 1. Understand the request
- Which layer is involved? (domain / database / ui)
- Is this a structural question, a state/flow problem, a DI wiring issue, or error handling?

### 2. Consult the right reference

| Topic | Reference File |
|-------|---------------|
| Layer responsibilities, dependency rule, what goes where | `references/clean-architecture.md` |
| ViewModel, UiState, Actions, MVI cycle, event handling | `references/mvi-mvvm.md` |
| UseCase design, validation vs operation, Either returns | `references/use-cases.md` |
| Repository interfaces, implementations, mappers | `references/repository-pattern.md` |
| StateFlow, SharedFlow, Flow operators, coroutine scopes | `references/flows-coroutines.md` |
| Modal stack, bottom sheets, open/close flow, modal events | `references/modal-bottom-sheets.md` |
| Koin: modules, viewModel {}, factory {}, single {}, KMP setup | `references/koin-di.md` |
| Arrow Either, error types, toUiText(), exception wrappers | `references/error-handling.md` |
| Unit tests, Flow testing, coroutine testing, fake repos | `references/testing.md` |

### 3. Apply and verify
- Confirm the dependency rule is respected (domain has zero dependencies on other layers)
- Ensure UiState is a single sealed/data class — never multiple state flags
- Check that coroutine scopes are appropriate (viewModelScope, not GlobalScope)
- Validate error types have `message` (English/logging) and `toUiText()` (UI/i18n)

### 4. Flag anti-patterns
Proactively point out violations found in existing code, even if the user didn't ask.

## Key Principles

1. **Dependency rule is absolute.** Domain depends on nothing. Data and UI depend on domain.
   Never import a Room entity, DAO, or Compose import inside `/domain/`.

2. **One UiState per screen.** Model screen state as a single `data class UiState(...)`.
   Avoid `isLoading: Boolean` + `error: String?` + `data: List<X>` as separate LiveData/flows.

3. **ViewModels expose, not decide.** Business logic lives in UseCases. ViewModels
   orchestrate: collect flows, call use cases, map results to UiState, emit Actions.

4. **Actions are one-shot events.** Use `Channel<Action>` consumed as `Flow<Action>`
   for navigation, toasts, dialogs — things that must not replay on recomposition.

5. **Coroutine scope discipline.** `viewModelScope` for UI-bound work. `CoroutineScope`
   injected via DI for repository/use-case level work. Never `GlobalScope`.

6. **Either over exceptions.** Prefer `Either<Error, Value>` for expected failures.
   Reserve exceptions (wrapped in `XxxException`) only for operation use cases that
   must propagate through coroutine boundaries.
