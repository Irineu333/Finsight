This is a Kotlin Multiplatform project targeting Android, Desktop (JVM) and iOS.

## Module structure

The app is modularized **by feature** in the **api/impl** pattern, on top of shared **core**
modules; dependency rules are enforced mechanically by convention plugins in `build-logic`.

- `build-logic/` — convention plugins (`finsight.kmp.library` / `compose.library` /
  `feature.api` / `feature.impl`).
- `core/*` — `common`, `ledger`, `model`, `navigation`, `resources`, `designsystem`, `ui`,
  `database`, `analytics`, `crashlytics`, `auth`.
- `feature/<name>/{api,impl}` — one pair per feature (support, categories, budgets,
  accounts, creditcards, recurring, transactions, report, dashboard). The `api` holds
  routes, repository/use-case interfaces and the `<Name>Entry`; the `impl` holds the
  screens, ViewModels, use cases, repositories and the feature's Koin module.
- `app/*` — the app split by responsibility: `shared` (KMP library shell/aggregator: root
  `App`, `AppNavHost`, Koin wiring via `appModules`), `android` (`com.android.application`),
  `desktop` (`kotlin("jvm")`), `ios` (KMP iOS-only framework, hosts
  `:app:ios:embedAndSignAppleFrameworkForXcode`).

> See **`feature/README.md`** for the normative dependency rules and entry-point pattern,
> and **`CLAUDE.md`** for the full module map.

## The ledger

Money is modeled as a **balanced double-entry ledger**, and that is the only model — no
balance stored in a column, no second way to compute a figure. It lives in `:core:ledger`,
which depends on no other project module: every write is a set of entries summing to zero,
every figure (balance, invoice owed, category spending, net worth) is `Σ entries`, and the
features are flavors of that one truth.

> The ledger is the source of truth, with accounting guarantees; the features are flavors
> of that truth, and the facades, the sugar.

> See **`core/ledger/README.md`** — the normative reference for the ledger: its vocabulary,
> read and write surfaces, the two ports and what is derived rather than persisted.

* [/app/shared](./app/shared/src) is the app shell — Compose entry points shared across targets.
  Platform entry points live in [/app/android](./app/android/src/main),
  [/app/desktop](./app/desktop/src/main) and [/app/ios](./app/ios/src/iosMain).

### Build and Run Android Application

To build and run the development version of the Android app, use the run configuration from the run widget
in your IDE’s toolbar or build it directly from the terminal:

- on macOS/Linux
  ```shell
  ./gradlew :app:android:assembleDebug
  ```
- on Windows
  ```shell
  .\gradlew.bat :app:android:assembleDebug
  ```

### Build and Run Desktop (JVM) Application

To build and run the development version of the desktop app, use the run configuration from the run widget
in your IDE’s toolbar or run it directly from the terminal:

- on macOS/Linux
  ```shell
  ./gradlew :app:desktop:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :app:desktop:run
  ```

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…