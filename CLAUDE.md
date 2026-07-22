# CLAUDE.md

## Project
Kotlin Multiplatform (Android/Desktop/iOS) finance app with Compose Multiplatform.

## Commands
```bash
./gradlew allTests                                          # All tests
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
- **`core/`** — `common` (util/extension/UiText/Platform/icons), `ledger` (the double-entry
  ledger: models, entities/DAOs, repositories and the write boundary — depends on no app
  module and **cannot name a facade**), `model` (facade models — category, card, invoice,
  installment, recurring, budget — their forms, errors and exceptions; depends on `ledger`),
  `navigation` (`LocalNavController` + the `NavRoute`/`NavGraphRoute`
  markers — no feature is ever named here), `resources` (single `Res`), `designsystem` (theme, `ModalManager`,
  generic components + shared modals like date/icon pickers), `ui` (components that render
  core models + shared UI models — never names a feature), `database` (the facade entities/DAOs,
  `AppDatabase` and every migration + shared mappers), `analytics`/`crashlytics`/`auth` (Firebase/
  no-op services).
- **`feature/<name>/api`** — routes (`@Serializable`), repository interfaces, public
  use-case interfaces, the `<Name>Entry` UI entry point. Depends only on `:core:*`.
- **`feature/<name>/impl`** — screens, ViewModels, modals, use cases, repository impls,
  mappers, the feature's Koin module and `NavGraphBuilder.<name>Graph()`. May depend on
  any `feature:*:api` and `:core:*`.
- **`app/`** — the app, split by responsibility:
  - **`:app:shared`** — KMP library, the shell/aggregator (the only module that sees
    `impl`s): `App` (theme, `LocalNavController`, `ModalManagerHost`, invokes `HomeChromeHost`),
    `AppNavHost` (only `<name>Graph()` calls), Koin aggregation (`appModules`). Declares no
    route, no `Scaffold`, no chrome. Under the `finsight.app.shared` convention plugin.
  - **`:app:android`** — `com.android.application` (non-KMP): `MainActivity`, `AndroidApp`
    (startKoin), Manifest, mipmaps, signing, google-services, crashlytics, versionCode/Name.
  - **`:app:desktop`** — `kotlin("jvm")`: `main.kt` + `compose.desktop` `nativeDistributions`.
  - **`:app:ios`** — KMP iOS-only: `MainViewController` + framework `ComposeApp`
    (exports `:core:*` + `feature:*:api`).
  - Koin bindings for cross-cutting singletons live in the owning core (`databaseModule` in
    `:core:database`, `commonModule` in `:core:common`, `designsystemModule` in
    `:core:designsystem`); `:app:shared` only aggregates.

Features: home (tab chrome: `HomeGraph`, `NavigationItem`, `HomeChromeHost`, FAB), support,
categories, budgets, accounts, creditcards (incl. invoices/installments/invoiceTransactions),
recurring, transactions, report, dashboard.

> Normative reference: **`feature/README.md`** (dependency rules, entry points, shell role).

## Conventions

**Architecture:** Clean Architecture + MVI/MVVM + Reactive Flows: ViewModels -> UiState + Actions

**Dependency Rule (modules):** (1) api ⊄ api, (2) impl ⊄ impl, (3) api ⊄ impl,
(4) impl → any api + `:core:*`; only `:app:shared` sees `impl`s. Cycles between features
are impossible by construction (star topology). **Layer rule (within a module):**
Domain <- Database, Domain <- UI.

**Derivation rule:** a rule that can be derived from the domain has exactly one owner, in
the domain. Features consume it; they never reimplement it. A consumer decides *whether* it
applies a rule — a screen may legitimately not offer what the domain allows — never *which*
rule it is.

**DI (Koin):** each feature `impl` exposes its module; the shell aggregates them.
`viewModel {}` screens, `factory {}` use cases, `single {}` repositories.

**Navigation:** type-safe `@Serializable` routes. A feature's `api` declares only its
*externally navigable* routes; internal destinations live in the `impl`. Each `impl` exposes
`NavGraphBuilder.<name>Graph()` — always a `navigation<<Name>Graph>` subgraph, even for a
single-screen feature — aggregated by the shell's single `AppNavHost`. `<Name>Graph` names a graph
node and implements `NavGraphRoute`; `<Name>Route` names a screen and implements `NavRoute` (both
markers live in `:core:navigation`, making every route findable by its implementations). Features navigate with
`LocalNavController.current.navigate(<Route from the target api>)`.

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

## Error Types (`core/model` — `domain/error/`; the ledger's own in `core/ledger`)
`enum class` or `sealed class` with:
- `val message: String` — English, for logging only
- `toUiText()` extension — internationalized via `UiText.Res`, for UI display
- `XxxException(val error: XxxError)` wrapper — **only** for operation use cases that can throw (e.g. `TransferBetweenAccountsUseCase`); validation use cases return the error type directly via `Either`

## Ledger (double-entry)
Money is modeled as a **balanced double-entry ledger**, and that is the only model. It lives
in **`:core:ledger`**, which depends on no app module — the separation is enforced by the
compiler, not by discipline (see below).
- **Chart of accounts:** every account and card is an `Account` with a `type` from the **closed** set `{ASSET, LIABILITY, INCOME, EXPENSE, EQUITY}` (`core/ledger`). A card is a facade linked to its `Account` by `accountId`, and reads closure from it — no copy. A **category is not in the chart**: it is a dimension (see below), so it owns its own `isArchived`. Beyond the user's accounts and cards the chart holds only three system rows: the two nominals every expense and income lands on, and reconciliation.
- **Entries:** a `Transaction` is a set of `Entry` (signed `Long` cents, debit-positive, `currency`). `Σ = 0` per currency is validated at the single write boundary (`LedgerEntryWriter`), alongside the dimension landing rule and whatever the registered `DimensionWriteGuard` refuses.
- **Reads:** every figure — balance, opening balance, invoice owed, category spending, net worth — is `Σ entries`, via `IEntryRepository`. There is no per-type sign rule and no second way to compute a number.
- **Derivation:** what a transaction *is* comes from the account types of its entries (`deriveTransactionLabel`), and the display sign from `AccountType.displaySign`. Neither is persisted.
- **Writes:** callers express **intent** by identity — `TransactionLeg(type, amount, accountId, dimensionId?)` plus a `ContraLeg(nature, dimensionId?)` for a one-sided intent. Resolving a facade to an id is the caller's job; completing and balancing the intent is the writer's, because it creates the system account on demand.
- **Dimensions:** a leg may carry one `dimensionId` — the analytic axis it is classified by. An invoice's dimension lands on the `LIABILITY` leg, a category's on the nominal one; `DimensionKind.landsOn` is the rule and the writer enforces it beside `Σ = 0`. "Uncategorized" is the *absence* of a dimension, never a bucket account.
- **The module boundary:** `:core:ledger` declares an internal `LedgerDatabase` listing only its four tables, so a `@Query` naming a facade table fails to compile (`no such table: invoices`). Two ports let a facade take part without the ledger knowing it exists: `DimensionWriteGuard` (veto a write touching my dimensions) and `TransactionRemovalHook` (a transaction was removed — correct yourself, in the same write transaction).

**Documented exceptions, both deliberate:** `Category.type` is primary state, not derived — "this is an expense category" is the user's declaration and nothing in the ledger produces it. And `transactions` retains the installment/recurring columns *without* foreign keys; they are grouping metadata, no ledger read consults them, and each facade's removal path nullifies them explicitly.

> Normative reference: **`core/ledger/README.md`** — the full vocabulary, the read and
> write surfaces with examples, the two ports, and what is derived rather than persisted.

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