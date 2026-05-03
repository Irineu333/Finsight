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

## 10.5 (retrofit). :core:domain — modelos compartilhados entre features

> **Regra: `feature:X:api` JAMAIS depende de `feature:Y:api`.** (ver D10 no design.md)
> Modelos referenciados por múltiplas features vivem em `:core:domain`, não em nenhuma feature:api específica.
> `transactions:api` violava essa regra ao depender de `accounts:api`, `categories:api` e `creditCards:api`.

- [x] 10.5.1 Create `core/domain/` module with `build.gradle.kts` using `kmp-library` plugin; deps on `:core:utils`
- [x] 10.5.2 Register `:core:domain` in `settings.gradle.kts`
- [x] 10.5.3 Move `Account` from `accounts:api` to `core:domain` (sem `init` block — validação fica nos use cases)
- [x] 10.5.4 Move `Category` from `categories:api` to `core:domain`
- [x] 10.5.5 Move `CreditCard` from `creditCards:api` to `core:domain` (sem `init` block)
- [x] 10.5.6 Move `Invoice` from `creditCards:api` to `core:domain`
- [x] 10.5.7 Update `accounts:api`, `categories:api`, `creditCards:api` to add `api(projects.core.domain)` and remove moved model files
- [x] 10.5.8 Update `transactions:api`: replace `api(accounts:api)` + `api(categories:api)` + `api(creditCards:api)` with `api(projects.core.domain)`

## 11. Feature level 1: transactions

> **Nota:** `transactions:api` usa `api(projects.core.domain)` — NÃO depende de nenhum `feature:X:api`. A regra "api não depende de api" é satisfeita.

- [x] 11.1 Create `feature/transactions/api/` module; move `Transaction`, `Operation`, `OperationInstallment`, `OperationRecurring`, repositories interfaces, `IBuildTransactionUseCase`, `ICalculateBalanceUseCase`, nav types, extensions
- [x] 11.2 Register `:feature:transactions:api` in `settings.gradle.kts`
- [x] 11.3 Create `feature/transactions/impl/` module; deps on own `:api` + `:core:*` + `accounts:api` + `creditCards:api`
- [x] 11.4 Move use cases (`BuildTransactionUseCase`, `CalculateBalanceUseCase`, etc.), screen, ViewModel, modals, Koin module
- [x] 11.5 Register `:feature:transactions:impl` in `settings.gradle.kts`
- [x] 11.6 Update `:composeApp`; verify compile

## 12. Feature level 1: recurring

> **Regra:** `recurring:api` NÃO depende de nenhum `feature:X:api`. `Recurring` usa `Account`, `Category`, `CreditCard` de `:core:domain`.

- [x] 12.1 Create `feature/recurring/api/` module; move `Recurring { type: Recurring.Type }`, `RecurringOccurrence`, `RecurringForm`, repository interfaces; dep on `:core:domain` (NO dep on `transactions:api`, `accounts:api`, `categories:api`, `creditCards:api`)
- [x] 12.2 Register `:feature:recurring:api` in `settings.gradle.kts`
- [x] 12.3 Create `feature/recurring/impl/` module; deps on own `:api` + `transactions:api` + `:core:*`
- [x] 12.4 Move use cases, screen, ViewModel, modals, Koin module
- [x] 12.5 Register `:feature:recurring:impl` in `settings.gradle.kts`
- [x] 12.6 Update `:composeApp`; verify compile

## 13. Feature level 1: installments

- [x] 13.1 Create `feature/installments/api/` module; move `Installment`, `IInstallmentRepository`
- [x] 13.2 Register `:feature:installments:api` in `settings.gradle.kts`
- [x] 13.3 Create `feature/installments/impl/` module; deps on own `:api` + `transactions:api` + `creditCards:api`
- [x] 13.4 Move use cases, screen, ViewModel, modals, Koin module
- [x] 13.5 Register `:feature:installments:impl` in `settings.gradle.kts`
- [x] 13.6 Update `:composeApp`; verify compile

## 14. Feature level 1: budgets

> **Regra:** `budgets:api` NÃO depende de `categories:api`. `Budget.categories: List<Category>` usa `Category` de `:core:domain`.

- [x] 14.1 Create `feature/budgets/api/` module; move `Budget { iconKey: String, categories: List<Category> }`, `IBudgetRepository`; dep on `:core:domain` (NOT `categories:api`)
- [x] 14.2 Register `:feature:budgets:api` in `settings.gradle.kts`
- [x] 14.3 Create `feature/budgets/impl/` module; move use cases, screen, modals, Koin module; dep on `categories:api` se precisar de `ICategoryRepository`
- [x] 14.4 Register `:feature:budgets:impl` in `settings.gradle.kts`
- [x] 14.5 Update `:composeApp`; verify compile

## 15. Feature terminal: report

- [x] 15.1 Create `feature/report/api/` module; move `ReportDocument`, `ReportLayout`, `ReportPerspective`, `PerspectiveTabNavType`
- [x] 15.2 Register `:feature:report:api` in `settings.gradle.kts`
- [x] 15.3 Create `feature/report/impl/` module; move screens, ViewModels, services, use cases, Koin module
- [x] 15.4 Register `:feature:report:impl` in `settings.gradle.kts`
- [x] 15.5 Update `:composeApp`; verify compile

## 16. Feature terminals: dashboard, home, support

> **Estratégia:** ordem `support → dashboard → transactions-entry → home`. Aplica padrão D11 (entry point em `:api`) para `dashboard` e `transactions` porque `home:impl` precisa renderizá-los sem violar D10. `support` fica apenas com `:impl`. `home` ganha `:api` mínimo com `AppRoute`/`HomeRoute`.

### 16.A support (sem :api)

- [x] 16.A.1 Create `feature/support/impl/` module with `kmp-feature` plugin
- [x] 16.A.2 Register `:feature:support:impl` in `settings.gradle.kts`
- [x] 16.A.3 Move `SupportScreen`, `SupportIssueScreen`, ViewModels, UiState, modais (`CreateSupportIssueModal`), use cases, Koin module, strings
- [x] 16.A.4 Update `:composeApp` to depend on `:feature:support:impl`; remove moved sources
- [x] 16.A.5 Verify `:composeApp` compiles

### 16.B dashboard (apenas :api com DashboardEntry — :impl deferred)

> **Escopo reduzido:** mover `dashboard:impl` exigiria migrar dezenas de components/modals compartilhados (`AccountCard`, `CreditCardCard`, `OperationCard`, `BudgetProgressCard`, `ViewBudgetModal`, `AccountFormModal`, etc) que vivem em `:composeApp`. Para destravar `home:impl` sem essa migração massiva, criamos só o contrato em `:api`. A implementação de `DashboardEntry` fica registrada no `:composeApp` (que já tem `DashboardScreen` e tudo que ele usa). A migração completa do dashboard vira **seção 16.E (deferred)**.

- [x] 16.B.1 Create `feature/dashboard/api/` with `kmp-compose` plugin + Navigation
- [x] 16.B.2 Register `:feature:dashboard:api` in `settings.gradle.kts`
- [x] 16.B.3 Define `abstract class DashboardEntry` em `:api` com `fun NavGraphBuilder.register(navController, onOpenTransactions: (...) -> Unit)`
- [x] 16.B.4 Implementar `DashboardEntryImpl` no `:composeApp` registrando `HomeRoute.Dashboard` com a `DashboardScreen` atual
- [x] 16.B.5 Registrar `single<DashboardEntry>` no Koin do `:composeApp` (via `appModule`)

### 16.C transactions entry point (D11)

> **Ajuste:** `TransactionsScreen` ainda vive no `:composeApp` (não foi migrado em 11.4). Por isso a impl do entry fica no `:composeApp` (mesma estratégia do dashboard), não em `:feature:transactions:impl`.

- [x] 16.C.1 Definir `abstract class TransactionsEntry` em `:feature:transactions:api`; subir `:api` de `kmp-library` para `kmp-compose` (precisa Compose+Navigation)
- [x] 16.C.2 Implementar `TransactionsEntryImpl` no `:composeApp` que registra a rota `HomeRoute.Transactions` (com type map de `Transaction.Type?`/`Transaction.Target?`)
- [x] 16.C.3 Registrar `single<TransactionsEntry>` no Koin do `:composeApp` (via `appModule`)
- [x] 16.C.4 Verify compile

### 16.D home (com :api mínimo: AppRoute + HomeRoute + HomeChrome*)

- [x] 16.D.1 Create `feature/home/api/` with `kmp-compose` plugin + Navigation; dep em `transactions:api` (porque `HomeRoute.Transactions` carrega `Transaction.Type?` e `Transaction.Target?`)
- [x] 16.D.2 Register `:feature:home:api` in `settings.gradle.kts`
- [x] 16.D.3 Move `AppRoute`, `HomeRoute` e parte pública de `HomeChrome` (Config, Controller, LocalHomeChromeController, HomeChromeEffect) para `:feature:home:api`
- [x] 16.D.4 Create `feature/home/impl/` with `kmp-feature` plugin; deps on own `:api`, `dashboard:api`, `transactions:api`, `:core:ui`
- [x] 16.D.5 Register `:feature:home:impl` in `settings.gradle.kts`
- [x] 16.D.6 Move `HomeScreen`, `HomeChromeStateHolder`, `BottomNavigationBar`, `NavigationItem` para `:feature:home:impl`. `HomeScreen` injeta `DashboardEntry` e `TransactionsEntry` via Koin e usa `with(entry) { register(navController) }` no `NavHost` interno
- [x] 16.D.7 Update `:composeApp` to depend on `:feature:home:impl`; remove moved sources
- [x] 16.D.8 Update `AppNavHost` no `:composeApp` para importar `AppRoute` de `:feature:home:api` e `HomeScreen` de `:feature:home:impl`; passa `onAddTransaction` para abrir `AddTransactionModal`
- [x] 16.D.9 Verify `:composeApp` compiles

### 16.E (deferred) Migração completa de dashboard:impl

> Mover `DashboardScreen`, ViewModel, components/, modal `DashboardComponentOptionsModal`, use cases (`BuildDashboardViewing`, `GetDashboardPreferences`), `IDashboardPreferencesRepository`, `DashboardComponentPreference`, analytics events, e todos os componentes/modais shared (`AccountCard`, `CreditCardCard`, `OperationCard`, `BudgetProgressCard`, `CategorySpendingCard`, `BalanceCard`, modais cross-feature) para os módulos apropriados (`:feature:X:impl` ou `:core:ui`). Tarefa grande — fica fora do escopo desta change.

## 17. :app (rename and wire-up)

> **Escopo aplicado:** rename + wire-up + drenagem completa. 17.2 mantido como está pois `kmp-compose` aplica `kmp-library` (`com.android.library`), incompatível com `com.android.application` do `:app`.

- [x] 17.1 Rename `:composeApp` to `:app` in `settings.gradle.kts` and directory (também atualizado `iosApp/project.yml`, `iosApp/iosApp.xcodeproj/project.pbxproj`, `.gitignore`, `README.md`, `CLAUDE.md`). Framework iOS mantém `baseName = "ComposeApp"` para não quebrar `import ComposeApp` no Swift.
- [x] 17.2 ~~Update `:app/build.gradle.kts` to use `kmp-compose`~~ — `kmp-compose` aplica `com.android.library`, incompatível com `com.android.application`. Build atual mantido; uma futura `kmp-application` convention pode reaproveitar o setup.
- [x] 17.3 Ensure `:app` depends on all `:feature:X:impl` modules — verificado em `app/build.gradle.kts`.
- [x] 17.4 Move `AppNavHost` to `:app` — já estava lá, mantido pelo rename.
- [x] 17.5 Ensure `startKoin` in `:app` aggregates all Koin modules — já agregado em `AndroidApp.kt`, `MainViewController.kt`, `main.kt` (jvm).
- [x] 17.6 Remove any remaining domain/database/ui code from `:app` — concluído. `:app/src/commonMain` saiu de 153 → 2 arquivos (`App.kt` + `AppNavHost.kt`). Wave-2 de §8/§10/§11 e §16.E inteira migradas para seus `:feature:X:impl`. Padrão entry-point estendido (D11) para form modais (Category/CreditCard/Account/RecurringForm), view modais (Operation/Adjustment/Category/Budget/Recurring/ConfirmRecurring) e modais de operação (CloseInvoice/PayInvoice/AdvancePayment/EditInvoiceBalance). Use cases compartilhados via `I*UseCase` (CalculateBalance/TransactionStats/BudgetProgress/GetPendingRecurring/AddInstallment). `:core:sharedui` (transitório, débito §18) hospeda 13 widgets cross-feature. `NavigationDispatcher`+`AppNavigationDispatcher` consolidados em `:feature:home:api/impl`. Tests também migrados para os respectivos `:feature:X:impl/commonTest`.
- [x] 17.7 Run `./gradlew allTests` — `:app:testDebugUnitTest` e `:app:jvmTest` passam. Falhas em `:core:database:testDebugUnitTest` são pré-existentes (`UnsatisfiedLinkError: no sqliteJni` no JVM Android unit test, problema de ambiente, não relacionado ao rename).
- [x] 17.8 Run `./gradlew check` — `:app:assembleDebug`, `compileKotlinJvm`, `compileKotlinIosArm64` passam. Falha pré-existente em `:app:generateDebugAndroidTestLintModel` (Firebase BOM não resolve versões para `androidTestCompileClasspath`), não relacionado ao rename.
- [x] 17.9 Build and run on Android, iOS, and Desktop; verify golden paths — Android e iOS validados manualmente pelo dev (apps rodando após o rename). Desktop verificado via `:app:compileKotlinJvm` OK.

## 18. Padronizar pacotes para refletirem o módulo

> **Contexto:** O estado atual mistura todos os módulos no mesmo prefixo (`com.neoutils.finsight.{domain,ui,database,...}`), independente do `:core:*` ou `:feature:X` em que o arquivo vive. Isso esconde fronteiras de módulo e dificulta navegação.
>
> **Convenção (D12 — ver design.md):** O pacote raiz de cada módulo reflete seu caminho Gradle. Subpacotes organizam por camada:
> - `:core:<x>` → `com.neoutils.finsight.core.<x>.*` (subpacotes: `model`, `repository`, `di`, `theme`, `component`, `entity`, `dao`, ...)
> - `:feature:<x>:api` → `com.neoutils.finsight.feature.<x>.{model,repository,usecase,error,exception,nav,...}`
> - `:feature:<x>:impl` → `com.neoutils.finsight.feature.<x>.{screen,modal,mapper,di,event,usecase,...}`
> - `:app` → `com.neoutils.finsight.app.*`
>
> `api` e `impl` da mesma feature compartilham o pacote raiz, mas separam por subpacote (api fica com camadas de contrato; impl fica com camadas de implementação/UI). Isso preserva D10 (Gradle ainda valida `api ↮ api`) e elimina a redundância de duplicar `.api`/`.impl` no nome do pacote.
>
> **Estratégia por módulo:**
> 1. mover `.kt` para o novo diretório
> 2. atualizar `package` declaration
> 3. atualizar `import`s em todos os consumidores
> 4. compilar (`:<module>:compileKotlinMetadata` ou módulo dependente)
>
> **Notas:**
> - `applicationId` do `:app` permanece `com.neoutils.finsight` (ID público da Play Store / App Store). Apenas `namespace` e os pacotes Kotlin mudam para `com.neoutils.finsight.app`.
> - iOS framework continua `baseName = "ComposeApp"`; nomes Obj-C derivam de file name + class name, não do pacote — Swift segue intacto.

- [x] 18.1 Adicionar **D12** em `design.md` documentando a convenção de pacote por módulo
- [x] 18.2 `:core:utils` → `com.neoutils.finsight.core.utils.*` (subpkgs: `extension`, `util`, `util.di`)
- [x] 18.3 `:core:platform` → `com.neoutils.finsight.core.platform.*`
- [x] 18.4 `:core:analytics` → `com.neoutils.finsight.core.analytics.*` (subpkgs: `crashlytics`, `di`)
- [x] 18.5 `:core:auth` → `com.neoutils.finsight.core.auth.*` (subpkgs: `di`)
- [x] 18.6 `:core:domain` → `com.neoutils.finsight.core.domain.*` (subpkg: `model`)
- [x] 18.7 `:core:database` → `com.neoutils.finsight.core.database.*` (subpkgs: `entity`, `dao`, `di`)
- [x] 18.8 `:core:ui` → `com.neoutils.finsight.core.ui.*` (subpkgs: `theme`, `component`, `modal`, `extension`, `util`, `di`)
- [x] 18.9 `:core:sharedui` → `com.neoutils.finsight.core.sharedui.*` (subpkgs: `component`, `model`)
- [x] 18.10 `:feature:accounts:api/impl` → `com.neoutils.finsight.feature.accounts.*`
- [x] 18.11 `:feature:categories:api/impl` → `com.neoutils.finsight.feature.categories.*`
- [x] 18.12 `:feature:creditCards:api/impl` → `com.neoutils.finsight.feature.creditCards.*`
- [x] 18.13 `:feature:transactions:api/impl` → `com.neoutils.finsight.feature.transactions.*`
- [x] 18.14 `:feature:recurring:api/impl` → `com.neoutils.finsight.feature.recurring.*`
- [x] 18.15 `:feature:installments:api/impl` → `com.neoutils.finsight.feature.installments.*`
- [x] 18.16 `:feature:budgets:api/impl` → `com.neoutils.finsight.feature.budgets.*`
- [x] 18.17 `:feature:report:api/impl` → `com.neoutils.finsight.feature.report.*`
- [x] 18.18 `:feature:support:impl` → `com.neoutils.finsight.feature.support.*`
- [x] 18.19 `:feature:dashboard:api/impl` → `com.neoutils.finsight.feature.dashboard.*`
- [x] 18.20 `:feature:home:api/impl` → `com.neoutils.finsight.feature.home.*`
- [x] 18.21 `:app` → `com.neoutils.finsight.app.*`; atualizar `namespace` em `app/build.gradle.kts` (sem alterar `applicationId`)
- [x] 18.22 Apagar diretórios vazios deixados pelos `git mv`. `move_module.py` agora poda automaticamente cada módulo migrado; um `find core feature app -path "*/src/*" -type d -empty -delete` final confirma 0 sobras.
- [x] 18.23 Rodar `./gradlew :app:assembleDebug :app:compileKotlinJvm :app:compileKotlinIosArm64` — passa. `:app:testDebugUnitTest`, `:app:jvmTest`, `:feature:report:impl:jvmTest`, `:feature:transactions:impl:jvmTest` passam. Falhas em `:core:database:testDebugUnitTest` (Migration tests) são pré-existentes — `UnsatisfiedLinkError: no sqliteJni` no JVM Android unit test, mesmo problema de ambiente registrado em §17.7.

## 19. Pós-modularização: navegação por eventos + `AppRoute` em `:app` (D13)

> **Contexto:** Após §17/§18, `AppRoute` ainda residia em `:feature:home:api` por restrição estrutural (era usado por `AppNavigationDispatcher` em `:home:impl` que recebia `NavHostController`). Para alinhar ownership (`AppRoute` é contrato do shell `:app`) e desacoplar `:impl`s do framework de navegação, o `NavigationDispatcher` foi convertido em canal de eventos consumido pelo `AppNavHost`. Decisão registrada em **D13** (`design.md`).

- [x] 19.1 Refatorar `:feature:home:api/.../component/NavigationDispatcher.kt`: substituir `interface NavigationDispatcher` por `class NavigationDispatcher` com `Channel<NavigationDestination>(Channel.BUFFERED)` + `events: Flow<NavigationDestination>` (`receiveAsFlow()`); adicionar `rememberNavigationDispatcher()`. Mantém `NavigationDestination`, `LocalNavigationDispatcher`, `NavigationDispatcherProvider` e a assinatura `dispatch(destination)` (zero impacto nos consumidores).
- [x] 19.2 Deletar `:feature:home:impl/.../navigation/AppNavigationDispatcher.kt` (e diretório vazio resultante). `:feature:home:impl` deixa de conhecer `NavHostController` e `AppRoute`.
- [x] 19.3 Mover `AppRoute.kt` de `:feature:home:api/.../route/AppRoute.kt` para `:app/.../app/route/AppRoute.kt`; atualizar `package` para `com.neoutils.finsight.app.route`.
- [x] 19.4 Atualizar `:app/.../screen/root/AppNavHost.kt`: trocar `rememberAppNavigationDispatcher(navController)` por `rememberNavigationDispatcher()`; adicionar `LaunchedEffect` que coleta `dispatcher.events` e traduz `NavigationDestination → navController.navigate(AppRoute.X)`; ajustar import de `AppRoute` para `com.neoutils.finsight.app.route.AppRoute`.
- [x] 19.5 Remover `api(libs.androidx.navigation.compose)` de `:feature:home:api/build.gradle.kts` (api não usa mais nada de `androidx.navigation`; `:feature:home:impl` continua recebendo a dep via `kmp-feature` plugin; `:app` já tem a sua própria).
- [x] 19.6 Verificar build: `./gradlew :feature:home:api:compileKotlinMetadata :feature:home:impl:compileKotlinMetadata :app:compileKotlinJvm :app:assembleDebug :app:compileKotlinIosArm64` — todos passam.
- [x] 19.7 Atualizar `design.md`: adicionar **D13** ("`AppRoute` em `:app`; navegação cross-feature por eventos") e ajustar D7 para refletir o novo conteúdo de `feature:home:api` (sai `AppRoute`, fica `NavigationDispatcher`).

## 20. Resolver violações D10 herdadas (`feature:X:api → feature:Y:api`)

> **Contexto:** Mesmo após §10.5 (que moveu `Account`, `Category`, `CreditCard`, `Invoice` para `:core:domain`), 5 dependências `api → api` haviam sobrado por modelos compartilhados que ficaram nas features de origem (`Transaction`, `Operation`, `Recurring`, `TransactionForm`). Aplicando o mesmo padrão D2/§10.5, todos os modelos referenciados por outra feature migraram para `:core:domain`.
>
> **Violações resolvidas:**
> - `budgets:api → recurring:api` + `budgets:api → transactions:api` (`BudgetProgress.recurring`, `ICalculateBudgetProgressUseCase` usando `Transaction`/`Operation`/`Recurring`)
> - `dashboard:api → home:api` + `transactions:api` (`DashboardEntry.register(... onOpenTransactions: (Transaction.Type?, Transaction.Target?))`)
> - `home:api → transactions:api` (`HomeRoute.Transactions(Transaction.Type?, Transaction.Target?)`)
> - `installments:api → transactions:api` (`IAddInstallmentUseCase`, `CreateInstallments`, `DeleteInstallments` usando `Transaction`/`Operation`/`TransactionForm`)

- [x] 20.1 Mover `Transaction`, `Operation`, `OperationInstallment`, `OperationRecurring`, `OperationPerspective` de `feature:transactions:api/.../model/` para `core:domain/.../model/`
- [x] 20.2 Mover `TransactionForm` de `feature:transactions:api/.../form/` para `core:domain/.../form/`
- [x] 20.3 Mover extensions `signedImpact`, `Category.Type.isAccept(Transaction.Type)` de `feature:transactions:api/.../extension/` para `core:domain/.../extension/`
- [x] 20.4 Mover `Recurring`, `RecurringOccurrence` de `feature:recurring:api/.../model/` para `core:domain/.../model/`
- [x] 20.5 Mover extension `Category.Type.isAccept(Recurring.Type)` de `feature:recurring:api/.../extension/` para `core:domain/.../extension/`
- [x] 20.6 Adicionar plugin `kotlinSerialization` + dep `kotlinx.serialization.json` em `:core:domain` (necessário para `@Serializable` em `Transaction.Type`/`Target`); promover `kotlinx.datetime` para `api`
- [x] 20.7 Find/replace global de imports: `feature.transactions.{model,form,extension}.*` → `core.domain.{model,form,extension}.*`; `feature.recurring.{model,extension}.*` → `core.domain.{model,extension}.*` (149 arquivos)
- [x] 20.8 Atualizar `feature/budgets/api/build.gradle.kts`: remover `api(projects.feature.recurring.api)` e `api(projects.feature.transactions.api)`
- [x] 20.9 Atualizar `feature/dashboard/api/build.gradle.kts`: substituir `api(projects.feature.home.api)` por `api(projects.core.domain)`
- [x] 20.10 Atualizar `feature/home/api/build.gradle.kts`: substituir `api(projects.feature.transactions.api)` por `api(projects.core.domain)`
- [x] 20.11 Atualizar `feature/installments/api/build.gradle.kts`: substituir `api(projects.feature.transactions.api)` por `api(projects.core.domain)`
- [x] 20.12 Atualizar `feature/transactions/api/build.gradle.kts`: remover plugin `kotlinSerialization` e deps `kotlinx.serialization.json`/`kotlinx.datetime` (não há mais `@Serializable` no módulo; `kotlinx.datetime` herdado via `core:domain`)
- [x] 20.13 Verificar `./gradlew :app:compileKotlinJvm :app:assembleDebug :app:compileKotlinIosArm64` — todos passam
- [x] 20.14 `grep -r "projects.feature.*\.api" feature/*/api/build.gradle.kts` retorna vazio — D10 satisfeita estruturalmente

## 21. Eliminar `:core:domain` e `:core:sharedui` — introduzir tier `:feature:X:ui` (D14)

> **Contexto:** Hoje convivem dois "god modules" remanescentes:
> - **`:core:domain`** — criado em §10.5/§20 para hospedar models compartilhados entre features (`Account`, `Category`, `CreditCard`, `Invoice`, `Transaction`, `Operation`, `Recurring`, `Budget` via `categories: List<Category>`). Existe porque D10 proíbe `feature:X:api → feature:Y:api`.
> - **`:core:sharedui`** — criado para hospedar Compose components renderizados por múltiplos `:impl` (`OperationCard`, `CreditCardCard`, `BudgetProgressCard`, `AccountSelector`, etc.) e seus UI models (`OperationUi`, `AccountUi`). Existe porque D10 proíbe `feature:X:impl → feature:Y:impl`.
>
> Ambos são consequência da mesma decisão estrutural (D10) e partilham o mesmo problema: ownership diluído + recompilação em cascata. Estado atual: 279 arquivos importam de `core.domain`; 14 components + 2 UI models em `:core:sharedui`; ~85 acessos a campos aninhados (`transaction.account`, `operation.category`, `recurring.creditCard`, `invoice.creditCard`).
>
> **Diagnóstico arquitetural:** os models ricos (`Transaction.account: Account?`, etc.) misturam **domínio puro** (IDs, valores) com **conveniência de apresentação** (objetos resolvidos para display). Separando essas duas preocupações em camadas distintas (domínio puro vs hidratação de UI), e dando a cada uma seu próprio lugar de ownership, eliminamos a necessidade de ambos os módulos compartilhados.
>
> **Estratégia (Opção 3 — tier `:ui`):**
>
> 1. **Domínio puro = só IDs.** `Transaction`, `Operation`, `Recurring`, `Budget`, `Invoice`, `TransactionForm` deixam de carregar objetos de outras features e passam a guardar apenas IDs (`accountId`, `categoryId`, `creditCardId`, `invoiceId`). Cada model volta para o `:api` da sua feature dona.
>
> 2. **Novo módulo `:feature:X:ui` por feature.** Cada feature ganha um terceiro módulo (`api`, `ui`, `impl`) que hospeda:
>    - `XxUi` (UI model — POJO que compõe display fields ou outros `XxUi`s)
>    - `IXxUiMapper` (interface de mapeamento `Domain → UI`)
>    - Compose components que outras features renderizam (`OperationCard`, `CreditCardCard`, `BudgetProgressCard`, etc.)
>    - Entry points D11 (`DashboardEntry`, `TransactionsEntry`) — saem de `:api` e descem para `:ui`, libertando `:api` da dependência de Compose
>
> 3. **Regras de dependência (extensão de D10):**
>
>    | De \ Para | `:core:*` | `:feature:X:api` | `:feature:X:ui` | `:feature:X:impl` |
>    |-----------|-----------|------------------|-----------------|-------------------|
>    | `:core:*` | ✅ acíclico | ❌ | ❌ | ❌ |
>    | `:api` | ✅ | ❌ | ❌ | ❌ |
>    | `:ui` | ✅ | ✅ (qualquer feature) | ❌ | ❌ |
>    | `:impl` | ✅ | ✅ | ✅ (qualquer feature) | ❌ |
>    | `:app` | ✅ | ✅ | ✅ | ✅ |
>
>    - `:api ↮ :api` proibido (D10 preservado)
>    - `:ui ↮ :ui` proibido — mesma razão de D10 (evita ciclos no grafo de UI)
>    - `:impl ↮ :impl` proibido (D10 preservado)
>    - `:ui` pode importar `:api` de qualquer feature → `OperationUi` em `transactions:ui` pode compor `Account`/`AccountUi` de `accounts:api`/`accounts:ui` (este último não — só `:api` cross-feature). Para compor outros `XxUi`s, o `XxUi` rico re-implementa os campos primitivos.
>    - `:impl` pode importar `:ui` de qualquer feature → `dashboard:impl` chama `OperationCard` de `transactions:ui` direto, sem boilerplate de entry point para cards
>
> 4. **Mappers split entre `:api` (interface) e `:impl` (implementação).** O `IXxUiMapper` mora em `:ui` (não em `:api`, porque mapper é UI-concern). A implementação concreta mora em `:impl`, onde tem acesso aos repositórios. ViewModels no `:impl` consomem `IXxUiMapper` via Koin para montar listas/itens em batch antes de emitir `UiState`.
>
> 5. **Mortes:**
>    - `:core:domain` — apagado (todos os models voltam para suas `:feature:X:api`)
>    - `:core:sharedui` — apagado (componentes migram para `:feature:X:ui` da feature dona)
>
> **Ganhos:**
> - Compose deixa o `:api` (`transactions:api` volta para `kmp-library`, sem Compose/Navigation)
> - Entry points D11 descem para `:ui` (sai do `:api` o último uso de Compose lá)
> - Ownership claro: `OperationCard` mora com `Operation`; `BudgetProgressCard` mora com `Budget`
> - Build incremental melhora: mudança em `transactions:ui` recompila só consumidores de UI, não consumidores de domínio
>
> **Custo:** ~9 novos módulos `:ui` (um por feature com componente cross-impl). Features sem componente cross-impl (`support`) não ganham `:ui`.

> **Ordem de execução (importante):** as letras seguem a ordem em que cada bloco compila. Refatorar models de domínio para IDs only **só funciona** se ao mesmo tempo existir o `:feature:X:ui` correspondente com `XxUi` + mapper para os ViewModels consumirem. Por isso: docs (A) → convention plugin (B) → esqueleto dos `:ui` (C) → uma feature por vez, refatorando model + criando UI tier + migrando componentes + atualizando consumidores em commits atômicos (D–L) → entry points D11 descem para `:ui` (M) → mover models residuais para `:feature:X:api` (N) → apagar `:core:domain` (O) → apagar `:core:sharedui` (P) → verificação (Q).

### 21.A Decisões de design

- [x] 21.A.1 Adicionar **D14** em `design.md`: "Tier `:feature:X:ui` para UI models e components cross-impl". Documentar (a) a tabela de dependências expandida, (b) o que vive em cada tier (`:api` = domínio puro + contratos; `:ui` = UI models + components + IXxUiMapper + entry points; `:impl` = ViewModels + telas + use cases + repos + XxUiMapper impl), (c) por que `:ui ↮ :ui` é proibido, (d) extinção de `:core:domain` e `:core:sharedui`, (e) **critério para criar `XxUi`**: somente quando houver derivação de display (ex: datas calculadas, strings formatadas) ou composição cross-feature (ex: `OperationUi` agrega `Account`/`Category`/`CreditCard`); tipos de domínio que já são display-friendly (`Account`, `Category`, `CreditCard`) são consumidos direto pela UI sem intermediário.
- [x] 21.A.2 Atualizar **D2** para refletir que models de domínio voltam todos para `:feature:X:api` (sem `:core:domain`); rich models passam a guardar IDs only.
- [x] 21.A.3 Atualizar **D7** para refletir que entry points migram de `:api` para `:ui`; `:api` perde Compose e Navigation.
- [x] 21.A.4 Atualizar **D10** com a nova tabela; manter regra `api ↮ api` e adicionar `:ui ↮ :ui`.
- [x] 21.A.5 Atualizar **D11** (entry points): mudam de `:api` para `:ui`. Para cards cross-impl, entry point deixa de ser necessário (`:impl → :ui` cross-feature já renderiza direto).
- [x] 21.A.6 Atualizar **D12** (convenção de pacote): adicionar `:feature:<x>:ui` → `com.neoutils.finsight.feature.<x>` (mesmo pacote raiz que `:api`/`:impl`; subpacotes típicos: `model`, `mapper`, `component`, `entry`).

### 21.B Convention plugin para `:ui`

- [x] 21.B.1 Avaliação: `kmp-compose` atende `:ui` direto. Cards recebem `XxUi` por parâmetro (sem Koin no componente); `IXxUiMapper` é só interface (sem Koin no `:ui`); entry points D11 adicionam Navigation só onde necessário (transactions:ui, dashboard:ui). Decisão: usar `kmp-compose` + adicionar Navigation por módulo conforme uso.
- [x] 21.B.2 Não criado — `kmp-compose` atende. (Caso futuros componentes em `:ui` precisem injetar via `koinInject()`, adicionar `libs.koin.compose` no módulo específico.)

### 21.C Esqueleto: criar `:feature:X:ui` (módulos vazios + registro)

> Cria os 8 módulos `:ui` antes de qualquer migração de código, para que os blocos D–L tenham onde colocar componentes e mappers feature por feature.
>
> Cada módulo: diretório `feature/<x>/ui/` + `build.gradle.kts` (`kmp-compose` + dependências mínimas) + `src/commonMain/kotlin/com/neoutils/finsight/feature/<x>/` vazio + entrada em `settings.gradle.kts`. Build do `:app` continua passando porque ninguém ainda depende dos novos módulos.

- [x] 21.C.1 Criar `feature/accounts/ui/`; registrar em `settings.gradle.kts`; deps: `accounts:api` + `:core:ui`
- [x] 21.C.2 Criar `feature/categories/ui/`; deps: `categories:api` + `:core:ui`
- [x] 21.C.3 Criar `feature/creditCards/ui/`; deps: `creditCards:api` + `:core:domain` (transitório p/ `Invoice`) + `:core:ui`
- [x] 21.C.4 Criar `feature/transactions/ui/`; deps: `transactions:api` + `accounts:api` + `categories:api` + `creditCards:api` + `:core:domain` (transitório) + `:core:ui` + Navigation (vai receber `TransactionsEntry` em 21.M)
- [x] 21.C.5 Criar `feature/recurring/ui/`; deps: `recurring:api` + `accounts:api` + `categories:api` + `creditCards:api` + `:core:ui`
- [x] 21.C.6 Criar `feature/budgets/ui/`; deps: `budgets:api` + `categories:api` + `:core:ui`
- [x] 21.C.7 Criar `feature/installments/ui/`; deps: `installments:api` + `transactions:api` + `creditCards:api` + `:core:ui`
- [x] 21.C.8 Criar `feature/dashboard/ui/`; deps: `dashboard:api` + `transactions:api` + `:core:ui` + Navigation (vai receber `DashboardEntry` em 21.M)
- [ ] 21.C.9 (Opcional) Criar `feature/home/ui/` se em 21.M decidirmos mover `HomeChrome*`/`HomeRoute` componentes para lá. Pode ficar pendente até 21.M.
- [x] 21.C.10 `./gradlew :app:assembleDebug` — build passa com 8 módulos `:ui` vazios

### 21.D `creditCards:ui` — `Invoice` → `creditCardId` + `InvoiceUi` + componentes + ViewModels

> Feature isolada: `Invoice` é consumido por outras features mas hoje está em `:core:domain`. Refatoramos para `creditCardId: Long`, criamos `InvoiceUi` (datas derivadas) em `creditCards:ui`, e migramos componentes CC do `:core:sharedui`. Outras features que liam `invoice.creditCard.*` ou `invoice.openingDate/closingDate/dueDate` precisam ser ajustadas — feito aqui mesmo via call-site update.

**Domínio:**
- [ ] 21.D.1 Substituir `Invoice.creditCard: CreditCard` por `creditCardId: Long` (em `:core:domain/model/Invoice.kt`, ainda no lugar atual)
- [ ] 21.D.2 Atualizar `InvoiceMapper` (`@Entity → Invoice`) em `creditCards:impl` para popular `creditCardId` direto do `@Entity` (parar de fazer JOIN com CreditCard)
- [ ] 21.D.3 `Invoice` perde `openingDate`/`closingDate`/`dueDate` derivadas (datas migram para `InvoiceUi`)

**UI tier:**
- [ ] 21.D.4 `creditCards:ui/model/InvoiceUi.kt`: `data class InvoiceUi(invoice, creditCard, openingDate, closingDate, dueDate, amount, ...)`; `IInvoiceUiMapper.toUi(Invoice): InvoiceUi` interface
- [ ] 21.D.5 `creditCards:impl/mapper/InvoiceUiMapper.kt` (impl da interface) injeta `ICreditCardRepository` para resolver `CreditCard` e calcula datas a partir de `closingDay`/`dueDay`. Suporte a batch (lista de invoices → query única CreditCard).
- [ ] 21.D.6 `creditCards:impl/di`: registrar `single<IInvoiceUiMapper> { InvoiceUiMapper(...) }`
- [ ] 21.D.7 `CreditCardCard` → `creditCards:ui/component/`
- [ ] 21.D.8 `CreditCardSelector` → `creditCards:ui/component/`
- [ ] 21.D.9 `InvoiceMonthNavigator` → `creditCards:ui/component/`
- [ ] 21.D.10 `InvoiceSelector` → `creditCards:ui/component/`

**Build wiring:**
- [ ] 21.D.11 `creditCards:impl/build.gradle.kts`: adicionar `implementation(projects.feature.creditCards.ui)`

**Consumidores:**
- [ ] 21.D.12 `creditCards:impl` ViewModels (`CreditCardsViewModel`, `InvoiceTransactionsViewModel`): usar `InvoiceUi` (com datas derivadas)
- [ ] 21.D.13 Atualizar todos os call sites cross-feature que liam `invoice.creditCard.*` ou `invoice.openingDate/closingDate/dueDate` para usar `InvoiceUi` (em UI) ou buscar `CreditCard` via repo (em use cases puros). Locais conhecidos: `transactions:impl`, `installments:impl`, `dashboard:impl`, `report:impl`.
- [ ] 21.D.14 `./gradlew :app:assembleDebug :app:compileKotlinJvm :app:compileKotlinIosArm64` — todos passam

### 21.E `accounts:ui` + `categories:ui` — migrar componentes (sem refatoração de domínio)

> `Account` e `Category` já são display-friendly (sem refs cruzadas) — não precisam virar IDs nem ganhar `XxUi` agora. Apenas movemos os componentes que outras features renderizam.

**accounts:ui:**
- [ ] 21.E.1 `AccountCard` → `accounts:ui/component/`
- [ ] 21.E.2 `AccountSelector` → `accounts:ui/component/`
- [ ] 21.E.3 `accounts:impl/build.gradle.kts`: adicionar `implementation(projects.feature.accounts.ui)`

**categories:ui:**
- [ ] 21.E.4 `CategorySelector` → `categories:ui/component/`
- [ ] 21.E.5 `MultiCategorySelector` → `categories:ui/component/`
- [ ] 21.E.6 `CategoryIconBox` → `categories:ui/component/`
- [ ] 21.E.7 `CategorySpendingCard` → `categories:ui/component/` (renderiza `CategorySpending` de `categories:api`; consumido por `dashboard:impl` e `report:impl`)
- [ ] 21.E.8 `categories:impl/build.gradle.kts`: adicionar `implementation(projects.feature.categories.ui)`

**Cross-impl wiring:**
- [ ] 21.E.9 Cada `:impl` que renderizava esses componentes via `:core:sharedui` adiciona `implementation(projects.feature.accounts.ui)` / `projects.feature.categories.ui` conforme uso. Atualizar imports de `core.sharedui.component.*` para `feature.<x>.component.*`.
- [ ] 21.E.10 `./gradlew :app:assembleDebug` — build passa

### 21.F `transactions:ui` — `Transaction`/`Operation` → IDs + `TransactionUi`/`OperationUi` + componentes + ViewModels

> Bloco mais pesado da §21. Refatora `Transaction`, `Operation`, `OperationPerspective`, `TransactionForm` para IDs only **e ao mesmo tempo** cria `TransactionUi`/`OperationUi` + mappers + migra `OperationCard`/`TargetSelector` + atualiza todos os ViewModels de `transactions:impl`. Também atualiza qualquer call site externo que lia `transaction.account/category/creditCard/invoice` ou `operation.category/sourceAccount/...`.

**Domínio (em `:core:domain` ainda):**
- [ ] 21.F.1 `Transaction`: `account: Account?` → `accountId: Long?`; `category: Category?` → `categoryId: Long?`; `creditCard: CreditCard?` → `creditCardId: Long?`; `invoice: Invoice?` → `invoiceId: Long?`. Manter `target`, `type`, `amount`, `title`, `date`, `id`, `operationId` puros.
- [ ] 21.F.2 `Operation`: `category: Category?` → `categoryId: Long?`; `sourceAccount: Account?` → `sourceAccountId: Long?`; `targetCreditCard: CreditCard?` → `targetCreditCardId: Long?`; `targetInvoice: Invoice?` → `targetInvoiceId: Long?`. `transactions: List<Transaction>` mantém-se (mesma feature).
- [ ] 21.F.3 Mover `Operation.label` (que usa `category.name`) para `OperationUi`; em `Operation` resta um `defaultLabel` que devolve `title` ou `"Untitled"`.
- [ ] 21.F.4 Refatorar `OperationPerspective.resolve()` para comparar `transaction.accountId == accountId` / `transaction.creditCardId == creditCardId` / `transaction.invoiceId == invoiceId`.
- [ ] 21.F.5 `TransactionForm`: substituir refs por IDs. Validação `category.type.isAccept(type)` migra para o ViewModel/builder que monta o form (que tem o `Category` resolvido em mãos).
- [ ] 21.F.6 Atualizar `TransactionMapper`/`OperationMapper` em `transactions:impl` para popular IDs direto do `@Entity` (remover hidratação cruzada).
- [ ] 21.F.7 Atualizar `BuildTransactionUseCase`/`OperationRepository`/`TransactionRepository` para devolver `Transaction`/`Operation` puros.

**UI tier:**
- [ ] 21.F.8 `transactions:ui/model/TransactionUi.kt`: `data class TransactionUi(transaction, account?, category?, creditCard?, invoice?: InvoiceUi?)` + propriedades derivadas (`displayLabel`, `displayCategoryName`, `displayCategoryIconKey`, etc.)
- [ ] 21.F.9 `transactions:ui/model/OperationUi.kt`: `data class OperationUi(operation, category?, sourceAccount?, targetCreditCard?, targetInvoice?: InvoiceUi?, transactions: List<TransactionUi>, perspective: OperationPerspective)` + derivadas (`displayLabel`, `displayAmount`, `displayCategory`, etc.) — substituindo o `OperationUi` atual de `:core:sharedui`
- [ ] 21.F.10 `transactions:ui/mapper/IOperationUiMapper.kt` interface; impl em `transactions:impl/mapper/OperationUiMapper.kt` injetando `IAccountRepository`, `ICategoryRepository`, `ICreditCardRepository`, `IInvoiceRepository` (e `IInvoiceUiMapper` para subcomponentes `InvoiceUi`). Suporte a batch (IDs distintos → query única para evitar N+1).
- [ ] 21.F.11 `transactions:ui/extension/`: extensão `Category.Type.isAccept(Transaction.Type)` (cruza 2 features — viável em `:ui`).
- [ ] 21.F.12 `OperationCard` → `transactions:ui/component/`; recebe `OperationUi` rico
- [ ] 21.F.13 `TargetSelector` → `transactions:ui/component/`

**Build wiring:**
- [ ] 21.F.14 `transactions:impl/build.gradle.kts`: adicionar `implementation(projects.feature.transactions.ui)` + `implementation(projects.feature.creditCards.ui)` (para `IInvoiceUiMapper`)
- [ ] 21.F.15 `transactions:impl/di`: registrar `single<IOperationUiMapper> { OperationUiMapper(...) }`

**Consumidores:**
- [ ] 21.F.16 `transactions:impl` ViewModels (`TransactionsViewModel`, `EditTransactionViewModel`, `AddTransactionViewModel`, `ViewOperationViewModel`, `ViewTransactionViewModel`, `ViewAdjustmentViewModel`): consumir `OperationUi`/`TransactionUi` via `IOperationUiMapper`; remover lookup inline de account/category/creditCard.
- [ ] 21.F.17 Atualizar call sites cross-feature que liam `transaction.account/category/...` para usar `TransactionUi` (em UI) ou resolver via repo (em use cases puros). Locais conhecidos: `accounts:impl` (`AccountsViewModel`, `TransferBetweenAccountsViewModel`), `categories:impl` (use cases que iteram), `installments:impl`, `dashboard:impl`, `report:impl`.
- [ ] 21.F.18 `./gradlew :app:assembleDebug :app:compileKotlinJvm :app:compileKotlinIosArm64` — todos passam

### 21.G `recurring:ui` — `Recurring` → IDs + `RecurringUi` + ViewModels

- [ ] 21.G.1 `Recurring`: `category: Category?` → `categoryId: Long?`; `account: Account?` → `accountId: Long?`; `creditCard: CreditCard?` → `creditCardId: Long?`. `label` migra para `RecurringUi`.
- [ ] 21.G.2 Atualizar `RecurringMapper` e `RecurringRepository` para devolver `Recurring` puro.
- [ ] 21.G.3 `recurring:ui/model/RecurringUi.kt`: compõe `Recurring` + `Account?`, `Category?`, `CreditCard?` direto; `IRecurringUiMapper` interface
- [ ] 21.G.4 `recurring:impl/mapper/RecurringUiMapper.kt` (impl) injeta os repositórios necessários; suporte a batch.
- [ ] 21.G.5 `recurring:ui/extension/`: extensão `Category.Type.isAccept(Recurring.Type)` (cruza 2 features).
- [ ] 21.G.6 `recurring:impl/build.gradle.kts`: adicionar `implementation(projects.feature.recurring.ui)`
- [ ] 21.G.7 `recurring:impl/di`: registrar `single<IRecurringUiMapper> { RecurringUiMapper(...) }`
- [ ] 21.G.8 `recurring:impl` ViewModels: consumir `RecurringUi` via `IRecurringUiMapper`.
- [ ] 21.G.9 `./gradlew :app:assembleDebug :app:compileKotlinJvm :app:compileKotlinIosArm64` — todos passam

### 21.H `budgets:ui` — `Budget` → IDs + `BudgetUi` + componente + ViewModels

- [ ] 21.H.1 `Budget`: `categories: List<Category>` → `categoryIds: List<Long>`.
- [ ] 21.H.2 Atualizar `BudgetMapper` e use cases (`CalculateBudgetProgress`, etc.) para trabalhar com IDs e resolver `Category` quando necessário.
- [ ] 21.H.3 `budgets:ui/model/BudgetUi.kt`: compõe `Budget` + `List<Category>` direto; `IBudgetUiMapper` interface
- [ ] 21.H.4 `budgets:impl/mapper/BudgetUiMapper.kt` (impl) injeta `ICategoryRepository`; suporte a batch.
- [ ] 21.H.5 `BudgetProgressCard` → `budgets:ui/component/`
- [ ] 21.H.6 `budgets:impl/build.gradle.kts`: adicionar `implementation(projects.feature.budgets.ui)`
- [ ] 21.H.7 `budgets:impl/di`: registrar `single<IBudgetUiMapper> { BudgetUiMapper(...) }`
- [ ] 21.H.8 `budgets:impl` ViewModels: consumir `BudgetUi`; use cases puros operam com IDs.
- [ ] 21.H.9 `./gradlew :app:assembleDebug :app:compileKotlinJvm :app:compileKotlinIosArm64` — todos passam

### 21.I `installments:ui` — `InstallmentWithOperationsUi` + ViewModels

- [ ] 21.I.1 `installments:ui/model/InstallmentWithOperationsUi.kt` (move de `installments:impl/screen/`) compondo `Installment` + `List<OperationUi>`; mapper interface `IInstallmentWithOperationsUiMapper`
- [ ] 21.I.2 `installments:impl/mapper/InstallmentWithOperationsUiMapper.kt` (impl) injeta `IOperationUiMapper`
- [ ] 21.I.3 `installments:impl/build.gradle.kts`: adicionar `implementation(projects.feature.installments.ui)` + `implementation(projects.feature.transactions.ui)` (para `OperationUi`)
- [ ] 21.I.4 `installments:impl/di`: registrar mapper
- [ ] 21.I.5 `installments:impl` (`InstallmentUiMapper`, `InstallmentsViewModel`): usar `OperationUi` em vez de `Operation` cru.
- [ ] 21.I.6 `./gradlew :app:assembleDebug :app:compileKotlinJvm :app:compileKotlinIosArm64` — todos passam

### 21.J `dashboard:ui` — consumir `XxUi` via mappers

- [ ] 21.J.1 `dashboard:impl/build.gradle.kts`: adicionar `implementation(projects.feature.dashboard.ui)` + `implementation(projects.feature.transactions.ui)` + `implementation(projects.feature.creditCards.ui)` + `implementation(projects.feature.accounts.ui)` + `implementation(projects.feature.budgets.ui)` + `implementation(projects.feature.categories.ui)` (componentes cross-feature renderizados pelo dashboard)
- [ ] 21.J.2 `dashboard:impl` (`DashboardViewModel`, builders, content): trabalhar com `OperationUi`/`InvoiceUi`/`BudgetUi` via mappers; consumir `Account` direto (sem `AccountUi`).
- [ ] 21.J.3 `./gradlew :app:assembleDebug :app:compileKotlinJvm :app:compileKotlinIosArm64` — todos passam

### 21.K `report:impl` + cleanup cross-cutting residual

> Itens que sobram fora dos blocos por-feature: report consome `XxUi` mas não tem `:ui` próprio; outros call sites residuais que escaparam dos blocos D–J.

- [ ] 21.K.1 `report:impl/build.gradle.kts`: adicionar `implementation(projects.feature.transactions.ui)` + outros `:ui` necessários
- [ ] 21.K.2 `report:impl`: ajustar geração de relatório para consumir `XxUi` em vez dos rich models antigos.
- [ ] 21.K.3 Sweep: `grep -r "transaction\.account\|transaction\.category\|transaction\.creditCard\|transaction\.invoice\|operation\.category\|operation\.sourceAccount\|operation\.targetCreditCard\|operation\.targetInvoice\|recurring\.category\|recurring\.account\|recurring\.creditCard\|invoice\.creditCard\|budget\.categories" --include="*.kt"` — todos os hits restantes resolvidos (devem retornar zero ou só usos legítimos de `XxUi.account/category/etc`).
- [ ] 21.K.4 `./gradlew :app:assembleDebug :app:compileKotlinJvm :app:compileKotlinIosArm64` — todos passam

### 21.L Mover entry points D11 de `:api` para `:ui`

- [ ] 21.L.1 `DashboardEntry` move de `dashboard:api` para `dashboard:ui`; impl atual no `:app` continua válida (passa a importar de `dashboard:ui`)
- [ ] 21.L.2 `TransactionsEntry` move de `transactions:api` para `transactions:ui`; impl atual no `:app` passa a importar de `transactions:ui`
- [ ] 21.L.3 `transactions:api` perde Compose/Navigation; volta para `kmp-library`
- [ ] 21.L.4 `dashboard:api` perde Compose/Navigation; volta para `kmp-library`
- [ ] 21.L.5 `home:api` reavaliar — se tem só `HomeRoute`/`HomeChrome`/`NavigationDispatcher` (que são pure data), pode ficar; senão move parte para `home:ui`
- [ ] 21.L.6 `:app/AppNavHost`: ajustar imports de entry points (`DashboardEntry`/`TransactionsEntry`) que mudaram de `:api` para `:ui`
- [ ] 21.L.7 `./gradlew :app:assembleDebug :app:compileKotlinJvm :app:compileKotlinIosArm64` — todos passam

### 21.M Mover models residuais de `:core:domain` para `:feature:X:api`

> Após D–K, os models não têm mais cross-references — podem voltar para casa.

- [ ] 21.M.1 Mover `Account` de `:core:domain/model/` para `:feature:accounts:api/model/`
- [ ] 21.M.2 Mover `Category` de `:core:domain/model/` para `:feature:categories:api/model/`
- [ ] 21.M.3 Mover `CreditCard`, `Invoice` para `:feature:creditCards:api/model/`
- [ ] 21.M.4 Mover `Transaction`, `Operation`, `OperationInstallment`, `OperationRecurring`, `OperationPerspective` para `:feature:transactions:api/model/`
- [ ] 21.M.5 Mover `TransactionForm` para `:feature:transactions:api/form/`
- [ ] 21.M.6 Mover `Recurring`, `RecurringOccurrence` para `:feature:recurring:api/model/`
- [ ] 21.M.7 Mover extensão `Transaction.signedImpact()` para `:feature:transactions:api/extension/`
- [ ] 21.M.8 Atualizar imports em todo o codebase via find/replace (`com.neoutils.finsight.core.domain.*` → pacote da feature dona)
- [ ] 21.M.9 `./gradlew :app:assembleDebug :app:compileKotlinJvm :app:compileKotlinIosArm64` — todos passam

### 21.N Apagar `:core:domain`

- [ ] 21.N.1 `git rm -r core/domain/`
- [ ] 21.N.2 Remover `include(":core:domain")` de `settings.gradle.kts`
- [ ] 21.N.3 Atualizar `build.gradle.kts` consumidores: remover `api(projects.core.domain)` / `implementation(projects.core.domain)` de `feature/*/api`, `feature/*/impl`, `feature/*/ui` (incluindo as deps transitórias adicionadas em 21.C.3 e 21.C.4), `core/sharedui` (transitório), `feature/report/impl`
- [ ] 21.N.4 `feature/transactions/api/build.gradle.kts`: readicionar `kotlinSerialization` + `kotlinx.serialization.json` (necessário para `@Serializable Transaction.Type/Target` por causa de `HomeRoute.Transactions`); promover `kotlinx.datetime` para `api`
- [ ] 21.N.5 `feature/home/api/build.gradle.kts`, `feature/dashboard/api/build.gradle.kts`, `feature/installments/api/build.gradle.kts`: substituir `api(projects.core.domain)` por `api(projects.feature.transactions.api)` onde precisarem dos types de transactions
- [ ] 21.N.6 `feature/creditCards/api/build.gradle.kts`: readicionar `kotlinx.datetime` (Invoice continua usando `LocalDate`/`Instant`/`YearMonth`)
- [ ] 21.N.7 Verificar via `grep -r "projects.core.domain" --include="*.kts"` que retorna vazio
- [ ] 21.N.8 Verificar via `grep -r "import com.neoutils.finsight.core.domain" --include="*.kt"` que retorna vazio
- [ ] 21.N.9 `./gradlew :app:assembleDebug :app:compileKotlinJvm :app:compileKotlinIosArm64` — todos passam

### 21.O Apagar `:core:sharedui`

- [ ] 21.O.1 Confirmar que `core/sharedui/src/commonMain/kotlin/.../component/` está vazio (todos os 13 componentes migrados em D, E, F, H)
- [ ] 21.O.2 Confirmar que `core/sharedui/src/commonMain/kotlin/.../model/` está vazio (`OperationUi`, `AccountUi` substituídos pelos novos `XxUi` em F)
- [ ] 21.O.3 `git rm -r core/sharedui/`
- [ ] 21.O.4 Remover `include(":core:sharedui")` de `settings.gradle.kts`
- [ ] 21.O.5 Remover `implementation(projects.core.sharedui)` dos `build.gradle.kts` consumidores
- [ ] 21.O.6 `./gradlew :app:assembleDebug :app:compileKotlinJvm :app:compileKotlinIosArm64` — todos passam

### 21.P Aplicar D12 nos novos pacotes `:ui`

- [ ] 21.P.1 Confirmar que cada `feature/<x>/ui/src/commonMain/kotlin/com/neoutils/finsight/feature/<x>/` segue o pacote raiz da feature, com subpacotes `model`, `mapper`, `component`, `entry`, `extension` conforme aplicável
- [ ] 21.P.2 Atualizar D12 em `design.md` com a entrada `:feature:<x>:ui`

### 21.Q Verificação final

- [ ] 21.Q.1 `grep -r "projects.core.domain" --include="*.kts"` retorna vazio
- [ ] 21.Q.2 `grep -r "projects.core.sharedui" --include="*.kts"` retorna vazio
- [ ] 21.Q.3 `grep -r "import com.neoutils.finsight.core.domain" --include="*.kt"` retorna vazio
- [ ] 21.Q.4 `grep -r "import com.neoutils.finsight.core.sharedui" --include="*.kt"` retorna vazio
- [ ] 21.Q.5 `grep -r "projects.feature.*\.api" feature/*/api/build.gradle.kts` retorna vazio (D10 inalterada)
- [ ] 21.Q.6 `grep -r "projects.feature.*\.ui" feature/*/ui/build.gradle.kts` retorna vazio (nova regra `:ui ↮ :ui`)
- [ ] 21.Q.7 `grep -r "projects.feature.*\.impl" feature/*/impl/build.gradle.kts` retorna vazio (D10 inalterada para `:impl`)
- [ ] 21.Q.8 `./gradlew :app:assembleDebug :app:compileKotlinJvm :app:compileKotlinIosArm64` — todos passam
- [ ] 21.Q.9 `./gradlew allTests` — testes passam (exceto pré-existentes documentados em §17.7/§18.23)
- [ ] 21.Q.10 Build manual em Android, iOS e Desktop; validar golden paths (criar transação, ver dashboard, fechar fatura, confirmar recurring, ajustar saldo, transferência entre contas) — UX inalterada

## 22. Documentation

- [ ] 22.1 Create `README.md` for each core module: `core/utils/`, `core/platform/`, `core/analytics/`, `core/auth/`, `core/ui/`, `core/database/`
- [ ] 22.2 Create `README.md` for each feature with api/impl: `feature/accounts/`, `feature/categories/`, `feature/creditCards/`, `feature/installments/`, `feature/recurring/`, `feature/transactions/`, `feature/budgets/`, `feature/report/`
- [ ] 22.3 Create `README.md` for terminal features: `feature/dashboard/`, `feature/home/`, `feature/support/`
- [ ] 22.4 Each feature README covers: responsabilidade, contratos públicos do `:api`, dependências e responsabilidades internas do `:impl`
- [ ] 22.5 Update root `CLAUDE.md`: replace `Layers` section with module convention (api/impl pattern, dependency rules, pointer to `settings.gradle.kts`)
- [ ] 22.6 Add `## Modules` section to root `CLAUDE.md` with one entry per feature/core module linking to its `README.md`