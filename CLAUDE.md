# CLAUDE.md

## Project
Kotlin Multiplatform (Android/Desktop/iOS) finance app with Compose Multiplatform.

## Commands
```bash
./gradlew allTests                                          # All tests
./gradlew check                                            # Verification
./gradlew :composeApp:testDebugUnitTest --tests "*.XxxTest" # Single test class
./gradlew :composeApp:testDebugUnitTest                    # Unit tests only
```

## Features
- **Dashboard**: balance overview, credit card summaries, account list
- **Transactions**: income/expense list with filters (account, category, month)
- **Accounts**: account management, balance adjustments, transfers between accounts
- **Credit Cards**: card management, invoice lifecycle (open/close/pay/reopen), invoice balance adjustments
- **Installments**: installment tracking across invoices
- **Recurring**: recurring transactions (confirm/skip/stop/reactivate)
- **Categories**: category management with icons, spending tracking
- **Budgets**: budget progress per category

**Layers:**
- `/domain/`: Repositories (interfaces), UseCases, models, Error types (business rules, framework-independent)
- `/database/`: Room entities, DAOs, Mappers, Repository implementations (data sources)
- `/ui/`: Screens (composables, ViewModels, UiState), Modals, Components (presentation)

## Useful Paths

**Extensions (`/extension/`):** Useful extensions for common types

**Utilities (`/util/`):** General-purpose utilities

## Conventions

**Architecture:** Clean Architecture + MVI/MVVM + Reactive Flows: ViewModels -> UiState + Actions

**Dependency Rule:** Domain <- Database, Domain <- UI (domain has no dependencies)

**DI (Koin):** `viewModel {}` screens, `factory {}` use cases, `single {}` repositories

**Navigation:** Type-safe sealed routes (App-level + Home-level nested)

**Modals:** `ModalManager` via `LocalModalManager`, extend `ModalBottomSheet`

**Error Handling:** Arrow library (Either/flatMap/catch)

> More details in the architecture skill.
> The iOS project uses **XcodeGen** (`iosApp/project.yml`).

## Strings & Internationalization

**`UiText`** (`/util/UiText`) — sealed class for UI-safe text:
- `UiText.asString()` — suspend, for non-Composable contexts
- `stringUiText(error: UiText): String` — `@Composable`, for UI display

**String resources:** `composeApp/src/commonMain/composeResources/values/strings.xml`

> Always use `UiText.Res` for user-facing messages. `UiText.Raw` only for dynamic/runtime values with no translation.

## Error Types (`/domain/error/`)
`enum class` or `sealed class` with:
- `val message: String` — English, for logging only
- `toUiText()` extension — internationalized via `UiText.Res`, for UI display
- `XxxException(val error: XxxError)` wrapper — **only** for operation use cases that can throw (e.g. `TransferBetweenAccountsUseCase`); validation use cases return the error type directly via `Either`

## E2E Tests (Maestro)

Testes black-box ponta-a-ponta para Android e iOS rodam via [Maestro](https://maestro.mobile.dev/) sobre o build flavor `e2e` (Auth/Firestore/Crashlytics/Analytics fakeados, sem rede). Detalhes, comandos e convenções em `.maestro/README.md`.

> **Ritual obrigatório — tela mudou ⇒ revise testTag e flow.** Ao alterar uma tela ou modal coberto por flow, atualize o `*TestTags.kt` da área e rode `maestro test .maestro/flows/<área>/` antes de abrir PR. Quebra de flow não é aceita silenciosamente.

## Code Style
- Write clear code; comments are the exception, not a crutch.
- Prefer simplicity to abstractions that increase complexity (overengineering), prioritizing:
    1. Do not duplicate logic
    2. Do not increase complexity
    3. Do not duplicate code
- Apply DRY with judgment, not mechanically. 
- Reuse code in an explicit way with low coupling, without hiding business decisions.
- Extract code when there is a clear responsibility, high cohesion, and low coupling. 
- Every change must maintain or improve code clarity.