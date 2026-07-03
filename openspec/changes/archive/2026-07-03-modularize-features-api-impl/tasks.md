# Tasks: Modularização por feature (api/impl)

> Regra transversal: cada extração de módulo é um commit atômico, sem mudança de comportamento.
> Ao fim de cada grupo, `./gradlew check` e build dos 3 targets (Android, iOS, Desktop) devem passar.

## 1. Fase 0 — build-logic e convenções

- [x] 1.1 Criar build incluído `build-logic` (limpar resquícios `.gradle` de `build-logic/` e `app/`) com version catalog compartilhado
- [x] 1.2 Implementar convention plugins `kmp.library` e `compose.library` (targets Android/iOS/Desktop, compiler options, Compose)
- [x] 1.3 Implementar convenções `feature.api` e `feature.impl` com verificação mecânica das regras de dependência (falha de build com mensagem indicando módulo e dependência proibida)
- [x] 1.4 Aplicar as convenções ao `:composeApp` atual e validar build verde nos 3 targets sem mover código

## 2. Fase 1 — módulos core

- [x] 2.1 Extrair `:core:common` (`util/`, `extension/`, `Platform.kt`, `UiText`) com source sets de plataforma
- [x] 2.2 Extrair `:core:model` (`domain/model/`, `domain/error/`, `domain/exception/`) — inclui extensions/NavTypes domain-coupled (`Category`, `Transaction`, `InvoiceExt`, `Transaction*NavType`)
- [x] 2.3 Extrair `:core:resources` movendo `composeResources` inteiro (sem split de conteúdo) e ajustando imports de `Res` (`publicResClass = true`)
- [x] 2.4 Extrair `:core:designsystem` (`ui/theme/`, `ModalManager`, componentes genéricos sem modelo de domínio) — `ui/icons/` foi para `:core:common` (usado por `domain/model`)
- [x] 2.5 Extrair `:core:ui` (componentes que renderizam modelos core: `AccountSelector`, `OperationCard`, seletores + `ui/model/`)
- [x] 2.6 Extrair `:core:database` (entities, DAOs, `AppDatabase`, `Converters`, expect/actual de builder por plataforma + migration tests). Mappers/repositories permanecem no shell (vão para os impls na Fase 3)
- [x] 2.7 Extrair `:core:analytics`, `:core:crashlytics` e `:core:auth` (interfaces de domínio + impls Firebase/no-op por plataforma + módulos Koin próprios)
- [x] 2.8 Verificação da fase: 3 targets compilam (jvm/android/iosSimulatorArm64) e testes unitários verdes. `check` completo falha apenas em passos de integração nativa Firebase (androidTest lint + link do binário de teste iOS `FirebaseCore`) — **pré-existente** (reproduzido no baseline da Fase 0), fora do escopo. `:composeApp` sem `domain/model`, `theme`, `database/` entities; resta `util/PerspectiveTabNavType` e `database/{mapper,repository}` (feature-coupled, saem na Fase 3)

## 3. Fase 2 — piloto :feature:support

- [x] 3.1 Criar `:feature:support:api` (rotas `@Serializable` `SupportRoute`/`SupportIssueRoute`, interface `ISupportRepository`). Sem `SupportEntry`: support não expõe modal a outras features (spec `feature-entry-points`), a superfície cross-feature é só a rota
- [x] 3.2 Criar `:feature:support:impl` (telas, ViewModels, `supportIssueForm` modal, `FirebaseSupportRepository`/`UnsupportedSupportRepository`, `supportModule` Koin com `supportPlatformModule` expect/actual para o repositório)
- [x] 3.3 Mecanismo de navegação decidido: extension `NavGraphBuilder.<feature>Graph(navController)` exposta pelo impl e agregada pelo shell no `AppNavHost` (documentado em `feature/README.md`)
- [x] 3.4 Export seletivo no framework iOS (`export()` de `:core:*` + `feature:support:api`; impls via `implementation`). Framework linka verde (`linkDebugFrameworkIosSimulatorArm64`); validação de símbolos no Swift limitada pelo ambiente (sem Xcode/Firebase nativo)
- [x] 3.5 Sem testes de support para migrar (só existem testes de report/common). Template validado: 3 targets compilam e testes unitários verdes

## 4. Fase 3 — ondas de features

- [x] 4.1 Extrair `:feature:categories` (api/impl, rotas, entry point, fatia dos módulos DI)
- [x] 4.2 Extrair `:feature:budgets` (api/impl; depende de `categories:api` para spending por categoria)
- [x] 4.3 Extrair `:feature:accounts` (api/impl; inclui transferências e ajustes de saldo)
- [x] 4.4 Extrair `:feature:creditcards` (api/impl; inclui invoices, installments e invoiceTransactions — a maior onda)
- [x] 4.5 Extrair `:feature:recurring` (api/impl; `recurring:impl` → `creditcards:api` para invoice do mês)
- [x] 4.6 Extrair `:feature:transactions` (api/impl; `transactions:impl` → `creditcards:api`, `accounts:api`, `categories:api`)
- [x] 4.7 Extrair `:feature:report` (api/impl; único impl com source sets de plataforma — print/share)
- [x] 4.8 Verificação da fase: 3 targets compilam e testes unitários verdes; regras de dependência impostas (nenhuma violação). Módulos DI por camada reduzidos ao shell/dashboard (`RepositoryModule`: Settings/CurrencyFormatter/DashboardPreferences; `ViewModelModule`: dashboard; `DatabaseModule`: DAOs). `UseCaseModule`/`MapperModule` migraram para `creditcards:impl`. Superfície de api ampliada além do design: interfaces de use case público (`GetOrCreateInvoiceForMonth`, `BuildTransaction`, `AddInstallment`, `InvoiceUiMapper`) e bindings transitórios de `Entry` no shell para resolver os ciclos de modais

## 5. Fase 4 — agregadores e shell

- [x] 5.1 Mover contrato `HomeChrome` para `:core:ui`
- [x] 5.2 Extrair `:feature:dashboard` (impl; consome entries + apis das demais features; `DashboardPreferences` junto). Sem api: dashboard é folha (nada o consome); `HomeScreen` compõe `DashboardScreen` diretamente (shell enxerga impl). `CreditCardsEntry` ampliada com os modais de fatura que o dashboard abre (payInvoice/advancePayment/closeInvoice/editInvoiceBalance)
- [x] 5.3 `AppRoute` reduzida a `Home` (todas as rotas de feature migraram para as apis donas); `HomeRoute` (abas Dashboard/Transactions) permanece no shell
- [x] 5.4 `:composeApp` reduzido a shell (App, AppNavHost, dispatcher, Home, `di/` de infra, entry points de plataforma, framework iOS). Módulos DI por camada mortos: `UseCaseModule`/`MapperModule` (→ creditcards), `RepositoryModule`/`ViewModelModule` (consolidados em `shellModule`: Settings/CurrencyFormatter/ModalManager/DebounceManager)
- [x] 5.5 Sem código de plataforma no `commonMain` do shell fora dos source sets: resta apenas `expect val databasePlatformModule` (com actuals nos source sets de plataforma) e os entry points de plataforma nos seus source sets

## 6. Verificação final

- [x] 6.1 Verificação: 3 targets compilam (jvm/android/iosSimulatorArm64), testes unitários verdes (`composeApp`, `core:database`, `feature:report:impl`) e framework iOS linka com todos os `export()`. `./gradlew check` completo permanece bloqueado apenas nos passos de integração nativa Firebase (androidTest lint + link do binário de teste iOS `FirebaseCore`) — pré-existente (reproduzido no baseline). Smoke test manual dos fluxos fica para execução interativa (não automatizável neste ambiente)
- [x] 6.2 Regras validadas com teste negativo: `api → api` proibida falha o build com mensagem indicando módulo e dependência (`api ':feature:accounts:api' não pode depender de ':feature:transactions:api'`)
- [x] 6.3 `feature/README.md` (seção "O papel do shell" + mecanismo de navegação + padrões emergentes), `CLAUDE.md` (mapa de módulos + regras) e `README.md` (estrutura modular) atualizados
