## 1. Preparation: build-logic and settings

- [x] 1.1 Create `build-logic/` directory with `settings.gradle.kts` and `build.gradle.kts`
- [x] 1.2 Create `build-logic/src/main/kotlin/kmp-library.gradle.kts` convention plugin (base: KMP targets + Android lib + Java 17)
- [x] 1.3 Create `build-logic/src/main/kotlin/kmp-compose.gradle.kts` convention plugin (applies kmp-library + Compose deps)
- [x] 1.4 Create `build-logic/src/main/kotlin/kmp-feature.gradle.kts` convention plugin (applies kmp-compose + Koin + Arrow + Navigation)
- [x] 1.5 ~~kmp-database~~ removido — `:core:database` configura Room/KSP diretamente no próprio `build.gradle.kts`
- [x] 1.6 Update root `settings.gradle.kts` to include `pluginManagement { includeBuild("build-logic") }`
- [x] 1.7 Verify `./gradlew help` resolves build-logic without errors

## 2. Breaking domain changes (inside :composeApp)

- [x] 2.1 Add `Recurring.Type { INCOME, EXPENSE }` enum inside `Recurring` model
- [x] 2.2 Update `RecurringForm` to use `Recurring.Type` instead of `Transaction.Type`
- [x] 2.3 Update `RecurringMapper` to convert `RecurringEntity.Type ↔ Recurring.Type`
- [x] 2.4 Replace `CategoryLazyIcon` field with `iconKey: String` in `Category` model
- [x] 2.5 Replace `CategoryLazyIcon` field with `iconKey: String` in `Budget` model
- [x] 2.6 Remove `CategoryLazyIcon` and `LazyIcon`; update UI usages to use `AppIcon.fromKey(iconKey).icon` (ImageVector) directly
- [x] 2.7 Run `./gradlew :composeApp:testDebugUnitTest` — all tests pass

## 3. :core:utils

- [x] 3.1 Create `core/utils/` module directory with `build.gradle.kts` using `kmp-library` plugin
- [x] 3.2 Register `:core:utils` in `settings.gradle.kts`
- [x] 3.3 Move pure Kotlin extensions: `YearMonth`, `Flow`, `Double`, `Instant`, `LocalDateTime` (`Validation` deferred to 6.4 — depends on `UiText` which has Compose)
- [x] 3.4 Move utilities: `ObservableMutableMap`, `DebounceManager`
- [x] 3.5 Add `:core:utils` dependency in `:composeApp`; remove moved sources from `:composeApp`
- [x] 3.6 Verify `:composeApp` compiles

## 4. :core:platform

- [x] 4.1 Create `core/platform/` module with `build.gradle.kts` using `kmp-library` plugin
- [x] 4.2 Register `:core:platform` in `settings.gradle.kts`
- [x] 4.3 Move `Platform`, `isDesktop` with expect/actual per platform (`PlatformContext` deferred to 6.x — JVM actual depends on `WindowScope` from Compose Desktop)
- [x] 4.4 Add `:core:platform` dependency in `:composeApp`; remove moved sources
- [x] 4.5 Verify `:composeApp` compiles

## 5. :core:analytics and :core:auth

- [x] 5.1 Create `core/analytics/` module with `build.gradle.kts` using `kmp-library` plugin
- [x] 5.2 Register `:core:analytics` in `settings.gradle.kts`
- [x] 5.3 Move `Analytics`, `Crashlytics`, `Event` interfaces + expect/actual DI platform modules
- [x] 5.4 Create `core/auth/` module with `build.gradle.kts` using `kmp-library` plugin
- [x] 5.5 Register `:core:auth` in `settings.gradle.kts`
- [x] 5.6 Move `AuthService` interface + expect/actual DI platform modules
- [x] 5.7 Add `:core:analytics` and `:core:auth` dependencies in `:composeApp`; remove moved sources
- [x] 5.8 Verify `:composeApp` compiles

## 6. :core:ui

- [x] 6.1 Create `core/ui/` module with `build.gradle.kts` using `kmp-compose` plugin (has Compose, sem Koin/Arrow/Navigation que são desnecessários para um módulo core)
- [x] 6.2 Register `:core:ui` in `settings.gradle.kts`; add deps on `:core:platform` + `:core:utils`
- [x] 6.3 Move theme, `UiText`, `AppIcon`, `CurrencyFormatter`, `LocalCurrencyFormatter`, `DateFormats`
- [x] 6.4 Move `PlatformContext` + `LocalPlatformContext` (deferred from 4.3 — JVM actual depends on Compose Desktop), input transformations, `ModalManager`, `NavigationDispatcher`, `NavigationDestination`; also move `Validation` (depends on `UiText`, deferred from 3.3)
- [x] 6.5 Move shared components sem dependência de domínio: `FormattingLocalsHost`, `BalanceCard`, `InstallmentCounter`, `IconPickerSelector`, `MonthSelector`, `MonthPickerDropdownMenu`, `SharedTransitionProvider`. Componentes acoplados a modelos de domínio (`AccountSelector`, `CategorySelector`, etc.) ficam no `:composeApp` até seções 8+.
- [x] 6.6 Add `:core:ui` dependency in `:composeApp`; remove moved sources
- [x] 6.7 Verify `:composeApp` compiles

## 7. :core:database

- [x] 7.1 Create `core/database/` module with `build.gradle.kts` using `kmp-library` plugin; adiciona Room + KSP manualmente
- [x] 7.2 Register `:core:database` in `settings.gradle.kts`; add dep on `:core:utils`
- [x] 7.3 Move `AppDatabase` and all `@Entity` classes
- [x] 7.4 Move all DAOs and Room `@Database` configuration
- [x] 7.5 ~~Move all mappers to `:core:database`~~ — Revisão arquitetural: mappers pertencem à feature (`feature:X:impl`), migram nas seções 8–16 junto com o domínio de cada feature
- [x] 7.6 ~~Move all repository implementations to `:core:database`~~ — Revisão arquitetural: repositórios implementam interfaces de domínio, pertencem à feature (`feature:X:impl`), migram nas seções 8–16
- [x] 7.7 Add `:core:database` dependency in `:composeApp`; remove moved sources
- [x] 7.8 Verify `:composeApp` compiles and Room KSP generates correctly

## 8. Feature level 0: accounts

> **Nota de implementação — split wave 1/wave 2:** `AccountsViewModel` e `TransferBetweenAccountsUseCase` dependem de `IOperationRepository` (de `transactions:api`, seção 11) e `ICategoryRepository` (de `categories:api`, seção 9), que ainda não existem. Por isso a migração de accounts é feita em duas ondas:
>
> - **Wave 1 (esta seção):** `accounts:api` + use cases puros (`Create`, `Validate`, `SetDefault`, `Update`, `Delete`, `EnsureDefault`) + `AccountMapper` + `AccountRepository` + modais `AccountForm`/`DeleteAccount` + analytics events + Koin module
> - **Wave 2 (após seção 11):** `AccountsScreen/ViewModel`, `TransferBetweenAccountsUseCase/Modal/ViewModel`, `AdjustBalance*`, `EditAccountBalance*`
>
> `IconPickerModal` foi movido para `:core:ui` (sem deps de domínio) para desbloquear o `AccountFormModal`.

- [x] 8.1 Create `feature/accounts/api/` with `build.gradle.kts` using `kmp-library` plugin
- [x] 8.2 Register `:feature:accounts:api` in `settings.gradle.kts`
- [x] 8.3 Move `Account`, `IAccountRepository`, `AccountError` (enum only, sem `toUiText`), `AccountException`, `IEnsureDefaultAccountUseCase` to `:feature:accounts:api`
- [x] 8.4 Create `feature/accounts/impl/` with `build.gradle.kts` using `kmp-feature` plugin; deps on own `:api` + `:core:*` (wave 1 — sem `AccountsScreen/ViewModel` e cross-feature use cases)
- [x] 8.5 Register `:feature:accounts:impl` in `settings.gradle.kts`
- [x] 8.6 Move wave-1 items: `AccountMapper`, `AccountRepository`, use cases puros, analytics events, `AccountFormModal/ViewModel`, `DeleteAccountModal/ViewModel`, `toUiText()` extension, Koin module, strings. Deferred: `AccountsScreen/ViewModel`, `TransferBetweenAccounts*`, `AdjustBalance*`, `EditAccountBalance*`
- [x] 8.7 Update `:composeApp` to depend on `:feature:accounts:impl`; remove moved sources
- [x] 8.8 Verify `:composeApp` compiles

## 9. Feature level 0: categories

- [x] 9.1 Create `feature/categories/api/` module; move `Category { iconKey: String }`, `ICategoryRepository`
- [x] 9.2 Register `:feature:categories:api` in `settings.gradle.kts`
- [x] 9.3 Create `feature/categories/impl/` module; move use cases, screen, ViewModel, modals, Koin module
- [x] 9.4 Register `:feature:categories:impl` in `settings.gradle.kts`
- [x] 9.5 Update `:composeApp`; verify compile

## 10. Feature level 0: creditCards

> **Nota de implementação — split wave 1/wave 2:** `CreditCardsScreen/ViewModel`, `DeleteCreditCardUseCase`, `CloseInvoiceUseCase`, `CalculateInvoiceUseCase`, `CalculateAvailableLimitUseCase`, `CalculateInvoiceOverviewsUseCase`, `AdjustInvoiceUseCase`, `DeleteFutureInvoiceUseCase`, `PayInvoicePaymentUseCase`, `AdvanceInvoicePaymentUseCase`, `DeleteCreditCardModal/ViewModel`, `InvoiceTransactionsScreen`, `InvoiceUiMapper` dependem de `ITransactionRepository`/`IOperationRepository`/`Transaction`/`Operation` (de `transactions:api`, seção 11) que ainda não existem. Por isso:
>
> - **Wave 1 (esta seção):** `CreditCardMapper`, `InvoiceMapper`, `CreditCardRepository`, `InvoiceRepository`, use cases puros (`ValidateCreditCardName`, `Add`, `Update`, `Open`, `Pay`, `Reopen`, `CreateInvoice`, `CreateFuture`, `CreateRetroactive`, `GetOrCreate`), `CreditCardForm`, `CreditCardPeriod`, `InvoiceUi`, `CreditCardUi`, `InvoiceExt`, analytics events, `CreditCardFormModal/ViewModel`, `CreditCardError.toUiText()`, Koin module
> - **Wave 2 (após seção 11):** `CreditCardsScreen/ViewModel`, `DeleteCreditCard*`, `CloseInvoice*`, `PayInvoicePayment*`, `AdvanceInvoicePayment*`, `AdjustInvoice*`, `DeleteFutureInvoice*`, `CalculateInvoice*`, `InvoiceTransactionsScreen`, `InvoiceUiMapper`

- [x] 10.1 Create `feature/creditCards/api/` module; move `CreditCard`, `Invoice`, `ICreditCardRepository`, `IInvoiceRepository`, `IGetOrCreateInvoiceForMonthUseCase`, `InvoiceExt`, errors
- [x] 10.2 Register `:feature:creditCards:api` in `settings.gradle.kts`
- [x] 10.3 Create `feature/creditCards/impl/` module; move wave-1 items; wave-2 items remain in `:composeApp` until seção 11
- [x] 10.4 Register `:feature:creditCards:impl` in `settings.gradle.kts`
- [x] 10.5 Update `:composeApp`; verify compile

## 11. Feature level 1: transactions

- [x] 11.1 Create `feature/transactions/api/` module; move `Transaction`, `Operation`, `OperationInstallment`, `OperationRecurring`, repositories interfaces, `IBuildTransactionUseCase`, `ICalculateBalanceUseCase`, nav types, extensions
- [x] 11.2 Register `:feature:transactions:api` in `settings.gradle.kts`
- [x] 11.3 Create `feature/transactions/impl/` module; deps on own `:api` + `:core:*` + `accounts:api` + `creditCards:api`
- [x] 11.4 Move use cases (`BuildTransactionUseCase`, `CalculateBalanceUseCase`, etc.), screen, ViewModel, modals, Koin module
- [x] 11.5 Register `:feature:transactions:impl` in `settings.gradle.kts`
- [x] 11.6 Update `:composeApp`; verify compile

## 12. Feature level 1: recurring

- [ ] 12.1 Create `feature/recurring/api/` module; move `Recurring { type: Recurring.Type }`, `RecurringOccurrence`, `RecurringForm`, repository interfaces (NO dep on `transactions:api`)
- [ ] 12.2 Register `:feature:recurring:api` in `settings.gradle.kts`
- [ ] 12.3 Create `feature/recurring/impl/` module; deps on own `:api` + `transactions:api` + `:core:*`
- [ ] 12.4 Move use cases, screen, ViewModel, modals, Koin module
- [ ] 12.5 Register `:feature:recurring:impl` in `settings.gradle.kts`
- [ ] 12.6 Update `:composeApp`; verify compile

## 13. Feature level 1: installments

- [ ] 13.1 Create `feature/installments/api/` module; move `Installment`, `IInstallmentRepository`
- [ ] 13.2 Register `:feature:installments:api` in `settings.gradle.kts`
- [ ] 13.3 Create `feature/installments/impl/` module; deps on own `:api` + `transactions:api` + `creditCards:api`
- [ ] 13.4 Move use cases, screen, ViewModel, modals, Koin module
- [ ] 13.5 Register `:feature:installments:impl` in `settings.gradle.kts`
- [ ] 13.6 Update `:composeApp`; verify compile

## 14. Feature level 1: budgets

- [ ] 14.1 Create `feature/budgets/api/` module; move `Budget { iconKey: String }`, `IBudgetRepository`; dep on `categories:api`
- [ ] 14.2 Register `:feature:budgets:api` in `settings.gradle.kts`
- [ ] 14.3 Create `feature/budgets/impl/` module; move use cases, screen, modals, Koin module
- [ ] 14.4 Register `:feature:budgets:impl` in `settings.gradle.kts`
- [ ] 14.5 Update `:composeApp`; verify compile

## 15. Feature terminal: report

- [ ] 15.1 Create `feature/report/api/` module; move `ReportDocument`, `ReportLayout`, `ReportPerspective`, `PerspectiveTabNavType`
- [ ] 15.2 Register `:feature:report:api` in `settings.gradle.kts`
- [ ] 15.3 Create `feature/report/impl/` module; move screens, ViewModels, services, use cases, Koin module
- [ ] 15.4 Register `:feature:report:impl` in `settings.gradle.kts`
- [ ] 15.5 Update `:composeApp`; verify compile

## 16. Feature terminals: dashboard, home, support

- [ ] 16.1 Create `feature/dashboard/impl/` module; deps on `accounts:api`, `creditCards:api`, `categories:api`, `budgets:api`, `recurring:api`, `transactions:api`
- [ ] 16.2 Move `DashboardScreen`, `DashboardViewModel`, components, modals, use cases, Koin module
- [ ] 16.3 Register `:feature:dashboard:impl` in `settings.gradle.kts`
- [ ] 16.4 Create `feature/home/impl/` module; move `HomeScreen`, `HomeRoute`, `AppRoute`, `HomeChrome`
- [ ] 16.5 Register `:feature:home:impl` in `settings.gradle.kts`
- [ ] 16.6 Create `feature/support/impl/` module; move `SupportScreen`, screens, use cases, Koin module
- [ ] 16.7 Register `:feature:support:impl` in `settings.gradle.kts`
- [ ] 16.8 Update `:composeApp`; verify compile

## 17. :app (rename and wire-up)

- [ ] 17.1 Rename `:composeApp` to `:app` in `settings.gradle.kts` and directory
- [ ] 17.2 Update `:app/build.gradle.kts` to use `kmp-compose` + `com.android.application` (não `library`); remove all individual KMP target declarations
- [ ] 17.3 Ensure `:app` depends on all `:feature:X:impl` modules
- [ ] 17.4 Move `AppNavHost` to `:app` referencing screens from each `:feature:X:impl`
- [ ] 17.5 Ensure `startKoin` in `:app` aggregates all Koin modules from each `:feature:X:impl`
- [ ] 17.6 Remove any remaining domain/database/ui code from `:app` (should only have entry points)
- [ ] 17.7 Run `./gradlew allTests` — all tests pass
- [ ] 17.8 Run `./gradlew check` — all verifications pass
- [ ] 17.9 Build and run on Android, iOS, and Desktop; verify golden paths for all features

## 18. Documentation

- [ ] 18.1 Create `README.md` for each core module: `core/utils/`, `core/platform/`, `core/analytics/`, `core/auth/`, `core/ui/`, `core/database/`
- [ ] 18.2 Create `README.md` for each feature with api/impl: `feature/accounts/`, `feature/categories/`, `feature/creditCards/`, `feature/installments/`, `feature/recurring/`, `feature/transactions/`, `feature/budgets/`, `feature/report/`
- [ ] 18.3 Create `README.md` for terminal features: `feature/dashboard/`, `feature/home/`, `feature/support/`
- [ ] 18.4 Each feature README covers: responsabilidade, contratos públicos do `:api`, dependências e responsabilidades internas do `:impl`
- [ ] 18.5 Update root `CLAUDE.md`: replace `Layers` section with module convention (api/impl pattern, dependency rules, pointer to `settings.gradle.kts`)
- [ ] 18.6 Add `## Modules` section to root `CLAUDE.md` with one entry per feature/core module linking to its `README.md`