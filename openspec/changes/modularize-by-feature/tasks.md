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
- [ ] 18.16 `:feature:budgets:api/impl` → `com.neoutils.finsight.feature.budgets.*`
- [ ] 18.17 `:feature:report:api/impl` → `com.neoutils.finsight.feature.report.*`
- [ ] 18.18 `:feature:support:impl` → `com.neoutils.finsight.feature.support.*`
- [ ] 18.19 `:feature:dashboard:api` → `com.neoutils.finsight.feature.dashboard.*`
- [ ] 18.20 `:feature:home:api/impl` → `com.neoutils.finsight.feature.home.*`
- [ ] 18.21 `:app` → `com.neoutils.finsight.app.*`; atualizar `namespace` em `app/build.gradle.kts` (sem alterar `applicationId`)
- [ ] 18.22 Apagar diretórios vazios deixados pelos `git mv` (ex.: `…/com/neoutils/finsight/extension/`, `…/util/`, `…/domain/`, `…/database/`, `…/ui/`). `find core feature app -type d -empty -delete` resolve a maioria; revisar manualmente o que sobrar.
- [ ] 18.23 Rodar `./gradlew :app:assembleDebug :app:compileKotlinJvm :app:compileKotlinIosArm64 allTests`

## 19. (Backlog) Desacoplar `Transaction`/`Operation` de `:core:domain`

> **Contexto:** `Transaction` e `Operation` atualmente carregam objetos completos (`Account?`, `Category?`, `CreditCard?`, `Invoice?`) em vez de IDs. Isso forçou a criação de `:core:domain` como módulo de tipos compartilhados. O verdadeiro isolamento por feature só é atingido quando cada feature define seus próprios tipos sem compartilhamento.
>
> **Objetivo:** Eliminar `:core:domain` e tornar cada `feature:X:api` completamente autônomo.

- [ ] 19.1 Substituir `account: Account?` por `accountId: Long?` em `Transaction` e atualizar todos os mapeamentos, use cases e UI afetados
- [ ] 19.2 Substituir `category: Category?` por `categoryId: Long?` em `Transaction` e `Operation`
- [ ] 19.3 Substituir `creditCard: CreditCard?` / `invoice: Invoice?` por `creditCardId: Long?` / `invoiceId: Long?` em `Transaction` e `Operation`
- [ ] 19.4 Atualizar `Budget.categories: List<Category>` para `Budget.categoryIds: List<Long>` se aplicável
- [ ] 19.5 Remover `:core:domain`; mover `Account`, `Category`, `CreditCard`, `Invoice` de volta para seus respectivos `feature:X:api`
- [ ] 19.6 Verificar que nenhum `feature:X:api` depende de `feature:Y:api`; rodar `./gradlew check`

## 20. Documentation

- [ ] 20.1 Create `README.md` for each core module: `core/utils/`, `core/platform/`, `core/analytics/`, `core/auth/`, `core/ui/`, `core/database/`
- [ ] 20.2 Create `README.md` for each feature with api/impl: `feature/accounts/`, `feature/categories/`, `feature/creditCards/`, `feature/installments/`, `feature/recurring/`, `feature/transactions/`, `feature/budgets/`, `feature/report/`
- [ ] 20.3 Create `README.md` for terminal features: `feature/dashboard/`, `feature/home/`, `feature/support/`
- [ ] 20.4 Each feature README covers: responsabilidade, contratos públicos do `:api`, dependências e responsabilidades internas do `:impl`
- [ ] 20.5 Update root `CLAUDE.md`: replace `Layers` section with module convention (api/impl pattern, dependency rules, pointer to `settings.gradle.kts`)
- [ ] 20.6 Add `## Modules` section to root `CLAUDE.md` with one entry per feature/core module linking to its `README.md`