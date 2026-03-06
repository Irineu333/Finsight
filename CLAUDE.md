# CLAUDE.md

## Project
Kotlin Multiplatform (Android/Desktop/iOS) finance app with Compose Multiplatform.

Package: `com.neoutils.finsight`
Module: `composeApp`

## iOS
The iOS project uses **XcodeGen** (`iosApp/project.yml`).

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

## Architecture
Clean Architecture + MVI/MVVM + Reactive Flows: ViewModels -> UiState + Actions

**Layers:**
- `/domain/`: Repositories (interfaces), UseCases, models, Error types (business rules, framework-independent)
- `/database/`: Room entities, DAOs, Mappers, Repository implementations (data sources)
- `/ui/`: Screens (composables, ViewModels, UiState), Modals, Components (presentation)

**Dependency Rule:** Domain <- Database, Domain <- UI (domain has no dependencies)

**DI (Koin):** `viewModel {}` screens, `factory {}` use cases, `single {}` repositories

**Navigation:** Type-safe sealed routes (App-level + Home-level nested)

**Modals:** `ModalManager` via `LocalModalManager`, extend `ModalBottomSheet`

**Error Handling:** Arrow library (Either/flatMap/catch)

## Strings & Internationalization

**`UiText`** (`com.neoutils.finsight.util.UiText`) — sealed class for UI-safe text:
```kotlin
UiText.Raw(value: String)                          // dynamic/runtime strings
UiText.Res(res: StringResource)                    // string resource (i18n)
UiText.ResWithArgs(res: StringResource, vararg args) // parameterized resource
```
- `UiText.asString()` — suspend, for non-Composable contexts
- `stringUiText(error: UiText): String` — `@Composable`, for UI display

**String resources:** `composeApp/src/commonMain/composeResources/values/strings.xml`
Always use `UiText.Res` for user-facing messages. `UiText.Raw` only for dynamic/runtime values with no translation.

## Error Types (`/domain/error/`)
`enum class` or `sealed class` with:
- `val message: String` — English, for logging only
- `toUiText()` extension — internationalized via `UiText.Res`, for UI display
- `XxxException(val error: XxxError)` wrapper — **only** for operation use cases that can throw (e.g. `TransferBetweenAccountsUseCase`); validation use cases return the error type directly via `Either`

## Code Style
- Documentation is the code (avoid comments, write clear code).
- Follow best programming practices (Return First Pattern, SOLID, DRY).
- High cohesion and low coupling.
- Don't make the code worse.