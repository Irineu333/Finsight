This is a Kotlin Multiplatform project targeting Android, Desktop (JVM) and iOS.

## Module structure

The app is modularized **by feature** in the **api/impl** pattern, on top of shared **core**
modules; dependency rules are enforced mechanically by convention plugins in `build-logic`.

- `build-logic/` — convention plugins (`finsight.kmp.library` / `compose.library` /
  `feature.api` / `feature.impl`).
- `core/*` — `common`, `model`, `resources`, `designsystem`, `ui`, `database`,
  `analytics`, `crashlytics`, `auth`.
- `feature/<name>/{api,impl}` — one pair per feature (support, categories, budgets,
  accounts, creditcards, recurring, transactions, report, dashboard). The `api` holds
  routes, repository/use-case interfaces and the `<Name>Entry`; the `impl` holds the
  screens, ViewModels, use cases, repositories and the feature's Koin module.
- `:composeApp` — the shell/aggregator: root `App`, `AppNavHost`, Koin wiring, platform
  entry points and the iOS framework (hosts `:composeApp:embedAndSignAppleFrameworkForXcode`).

> See **`feature/README.md`** for the normative dependency rules and entry-point pattern,
> and **`CLAUDE.md`** for the full module map.

* [/composeApp](./composeApp/src) is the app shell — Compose entry points shared across targets.
  It contains several subfolders:
    - [commonMain](./composeApp/src/commonMain/kotlin) is for code that’s common for all targets.
    - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
      For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
      the [iosMain](./composeApp/src/iosMain/kotlin) folder would be the right place for such calls.
      Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./composeApp/src/jvmMain/kotlin)
      folder is the appropriate location.

### Build and Run Android Application

To build and run the development version of the Android app, use the run configuration from the run widget
in your IDE’s toolbar or build it directly from the terminal:

- on macOS/Linux
  ```shell
  ./gradlew :composeApp:assembleDebug
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:assembleDebug
  ```

### Build and Run Desktop (JVM) Application

To build and run the development version of the desktop app, use the run configuration from the run widget
in your IDE’s toolbar or run it directly from the terminal:

- on macOS/Linux
  ```shell
  ./gradlew :composeApp:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:run
  ```

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…