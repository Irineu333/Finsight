# Modal Bottom Sheet Pattern

## Purpose

This document defines the modal architecture pattern used in this project.
It is prescriptive for all new `ui/modal/*` features.

## Core Architecture

### 1. Centralized modal stack

- Use a single `ModalManager` instance registered in DI (`single { ModalManager() }`).
- Render modals through `ModalManagerHost` at app navigation root.
- Open modals from anywhere in UI using `LocalModalManager.current.show(...)`.

Reference implementation:
- `ui/component/ModalManager.kt`
- `ui/screen/home/HomeScreen.kt` (`ModalManagerHost` wrapping app content)
- `di/ViewModelModule.kt` (`single { ModalManager() }`)

### 2. Modal base class

All modals must extend `ModalBottomSheet`.

Why:
- Standardized Material 3 bottom sheet behavior.
- Isolated `ViewModelStore` per modal instance.
- Automatic cleanup on dismiss (`viewModelStore.clear()` in `onDismissed()`).

## Creation Rules

### Modal class responsibilities

- Keep modal input in constructor parameters (e.g. selected item, ids, callbacks).
- Compose content inside `BottomSheetContent()`.
- Do not expose mutable UI state from the modal class itself; delegate to ViewModel when stateful.

### ViewModel acquisition

- Use `koinViewModel<YourModalViewModel>()`.
- When modal has constructor input, pass it using `parametersOf(...)`.

Example:

```kotlin
val viewModel = koinViewModel<EditTransactionViewModel> { parametersOf(transaction) }
```

## Interaction Pattern

### Open modal

- Screen/component action -> `modalManager.show(YourModal(...))`.
- Nested modal is allowed from inside another modal (date picker, icon picker, form helper).

### Close modal

- `modalManager.dismiss()` closes only the top modal.
- `modalManager.dismissAll()` closes the full stack.

Use `dismissAll()` when:
- The parent modal content becomes stale after success (e.g. edit/delete flows opened from detail modal).
- Navigating away from the current modal context.

Use `dismiss()` when:
- Only the current modal should close and parent context must stay open.

## State, Actions, and Events

### ViewModel style

- For complex modal flows, expose:
  - one `uiState: StateFlow<...>`
  - one `onAction(action: XxxAction)` entrypoint
- For simple confirmation modals, a single command method is acceptable.

### One-shot effects

Use `Channel<Event>(Channel.BUFFERED)` + `receiveAsFlow()` for:
- Snackbar messages
- Open dependent modal
- Navigation requests

Collect events in modal composable via `LaunchedEffect(viewModel)`.

## Navigation from Modals

- Use `LocalNavigator.current.navigate(...)` instead of direct nav controller usage inside modal.
- Dismiss stack first when navigation should leave modal context:
  - `modalManager.dismissAll()`
  - `navigator.navigate(...)`

## Two Supported Modal Types

### 1. Stateful modal (ViewModel-backed)

Use for forms, validation, repository/use case interaction, or async logic.
Examples:
- `AddTransactionModal`
- `EditTransactionModal`
- `TransferBetweenAccountsModal`

### 2. Callback modal (stateless/lightweight)

Use when the modal only returns a local selection and has no data-layer logic.
Examples:
- `DatePickerModal`
- `DateRangePickerModal`
- `IconPickerModal`

## Anti-patterns

- Creating local `Boolean` flags on screens to control bottom sheet visibility instead of `ModalManager`.
- Instantiating Material `ModalBottomSheet` directly in each feature.
- Keeping business logic in the modal composable.
- Using `dismissAll()` by default for every success path.
- Navigating from modal without clearing the modal stack when context should end.

## Checklist for New Modals

- Modal extends `ModalBottomSheet`.
- ViewModel is injected with Koin (and `parametersOf` when needed).
- Complex interactions go through `onAction`.
- One-shot effects use `Channel + receiveAsFlow`.
- Success dismiss behavior (`dismiss` vs `dismissAll`) matches UX flow.
- Nested modal usage is intentional and bounded (no untracked loops).
