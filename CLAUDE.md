# CLAUDE.md

## Project
Kotlin Multiplatform (Android/Desktop/iOS) finance app with Compose Multiplatform.

## Commands
```bash
./gradlew allTests                                          # All tests
./gradlew check                                            # Verification
./gradlew :app:shared:testDebugUnitTest --tests "*.XxxTest" # Single test class
./gradlew :app:shared:testDebugUnitTest                    # Unit tests only
./gradlew :app:android:assembleDebug                       # Build Android APK
./gradlew :app:desktop:run                                 # Run Desktop app
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

## Module structure (feature api/impl + core + app)

The app is modularized by **feature** in the **api/impl** pattern, on top of a set of
**core** modules, with the app split into single-responsibility `app/` modules. Rules are
enforced mechanically by convention plugins in `build-logic`
(`finsight.kmp.library` / `compose.library` / `feature.api` / `feature.impl` / `app.shared`).

- **`build-logic/`** — convention plugins; a feature `build.gradle.kts` is ~5 lines.
- **`core/`** — `common` (util/extension/UiText/Platform/icons), `model` (domain models,
  errors, exceptions), `navigation` (`LocalNavController` — the navigation channel, no feature
  is ever named here), `resources` (single `Res`), `designsystem` (theme, `ModalManager`,
  generic components + shared modals like date/icon pickers), `ui` (components that render
  core models + shared UI models + `HomeChrome`), `database` (Room entities/DAOs/
  `AppDatabase`/converters + shared mappers), `analytics`/`crashlytics`/`auth` (Firebase/
  no-op services).
- **`feature/<name>/api`** — routes (`@Serializable`), repository interfaces, public
  use-case interfaces, the `<Name>Entry` UI entry point. Depends only on `:core:*`.
- **`feature/<name>/impl`** — screens, ViewModels, modals, use cases, repository impls,
  mappers, the feature's Koin module and `NavGraphBuilder.<name>Graph()`. May depend on
  any `feature:*:api` and `:core:*`.
- **`app/`** — the app, split by responsibility:
  - **`:app:shared`** — KMP library, the shell/aggregator (the only module that sees
    `impl`s): `App` (hosting the Home `Scaffold`), `AppNavHost`, `HomeGraph`/`NavigationItem`,
    Koin aggregation (`appModules`).
    Under the `finsight.app.shared` convention plugin.
  - **`:app:android`** — `com.android.application` (non-KMP): `MainActivity`, `AndroidApp`
    (startKoin), Manifest, mipmaps, signing, google-services, crashlytics, versionCode/Name.
  - **`:app:desktop`** — `kotlin("jvm")`: `main.kt` + `compose.desktop` `nativeDistributions`.
  - **`:app:ios`** — KMP iOS-only: `MainViewController` + framework `ComposeApp`
    (exports `:core:*` + `feature:*:api`).
  - Koin bindings for cross-cutting singletons live in the owning core (`databaseModule` in
    `:core:database`, `commonModule` in `:core:common`, `designsystemModule` in
    `:core:designsystem`); `:app:shared` only aggregates.

Features: support, categories, budgets, accounts, creditcards (incl. invoices/
installments/invoiceTransactions), recurring, transactions, report, dashboard.

> Normative reference: **`feature/README.md`** (dependency rules, entry points, shell role).

## Conventions

**Architecture:** Clean Architecture + MVI/MVVM + Reactive Flows: ViewModels -> UiState + Actions

**Dependency Rule (modules):** (1) api ⊄ api, (2) impl ⊄ impl, (3) api ⊄ impl,
(4) impl → any api + `:core:*`; only `:app:shared` sees `impl`s. Cycles between features
are impossible by construction (star topology). **Layer rule (within a module):**
Domain <- Database, Domain <- UI.

**DI (Koin):** each feature `impl` exposes its module; the shell aggregates them.
`viewModel {}` screens, `factory {}` use cases, `single {}` repositories.

**Navigation:** type-safe `@Serializable` routes. A feature's `api` declares only its
*externally navigable* routes; internal destinations live in the `impl`. Each `impl` exposes
`NavGraphBuilder.<name>Graph()` aggregated by the shell's single `AppNavHost`. Features navigate
with `LocalNavController.current.navigate(<Route from the target api>)`.

**Modals:** `ModalManager` via `LocalModalManager`, extend `ModalBottomSheet`

**Error Handling:** Arrow library (Either/flatMap/catch)

> More details in the architecture skill.
> The iOS project uses **XcodeGen** (`iosApp/project.yml`).

## Strings & Internationalization

**`UiText`** (`core/common` — `util/UiText`) — sealed class for UI-safe text:
- `UiText.asString()` — suspend, for non-Composable contexts
- `stringUiText(error: UiText): String` — `@Composable`, for UI display

**String resources:** `core/resources/src/commonMain/composeResources/values/strings.xml`

> Always use `UiText.Res` for user-facing messages. `UiText.Raw` only for dynamic/runtime values with no translation.

## Error Types (`core/model` — `domain/error/`)
`enum class` or `sealed class` with:
- `val message: String` — English, for logging only
- `toUiText()` extension — internationalized via `UiText.Res`, for UI display
- `XxxException(val error: XxxError)` wrapper — **only** for operation use cases that can throw (e.g. `TransferBetweenAccountsUseCase`); validation use cases return the error type directly via `Either`

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