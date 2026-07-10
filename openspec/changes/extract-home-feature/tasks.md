## 1. Esqueleto dos módulos

Cria os módulos vazios e os registra. Ao fim deste grupo o projeto compila igual a antes — nada foi movido.

- [ ] 1.1 Criar `feature/dashboard/api/build.gradle.kts` com `id("finsight.feature.api")`, dependências `projects.core.navigation` + `api(libs.androidx.navigation.compose)` (o `register` expõe `NavGraphBuilder`) e `projects.core.designsystem`
- [ ] 1.2 Criar `feature/home/api/build.gradle.kts` com `id("finsight.feature.api")` e dependências `projects.core.navigation`, `projects.core.designsystem`
- [ ] 1.3 Criar `feature/home/impl/build.gradle.kts` com `id("finsight.feature.impl")` e dependências `projects.core.{common,model,navigation,designsystem,ui,resources,analytics}` + `projects.feature.home.api`, `projects.feature.dashboard.api`, `projects.feature.transactions.api`
- [ ] 1.4 Registrar `:feature:dashboard:api`, `:feature:home:api` e `:feature:home:impl` no `settings.gradle.kts`
- [ ] 1.5 Adicionar `projects.feature.dashboard.api` como dependência de `feature/dashboard/impl` e `projects.feature.home.{api,impl}` a `app/shared/build.gradle.kts`
- [ ] 1.6 Adicionar `:feature:home:api` e `:feature:dashboard:api` ao `export()` do framework `ComposeApp` em `app/ios/build.gradle.kts`
- [ ] 1.7 Rodar `./gradlew check` — deve passar com os módulos vazios

## 2. `dashboard:api` e `DashboardEntry`

- [ ] 2.1 Mover `DashboardRoute` de `feature/dashboard/impl/.../ui/screen/dashboard/DashboardRoute.kt` para `feature/dashboard/api`
- [ ] 2.2 Criar `DashboardEntry` em `feature/dashboard/api` com `fun register(builder: NavGraphBuilder)`
- [ ] 2.3 Criar `DashboardEntryImpl` (internal) em `dashboard:impl` delegando a `NavGraphBuilder.dashboardGraph()`; tornar `dashboardGraph()` internal
- [ ] 2.4 Registrar `single<DashboardEntry> { DashboardEntryImpl() }` no `dashboardModule`
- [ ] 2.5 Rodar `./gradlew check` — `AppNavHost` ainda chama `dashboardGraph()` diretamente, então este passo mantém a extensão pública até 5.2, se necessário

## 3. `TransactionsEntry` ganha grafo e modal

- [ ] 3.1 Adicionar `fun register(builder: NavGraphBuilder)` e `fun addTransactionModal(): Modal` a `TransactionsEntry` em `feature/transactions/api`
- [ ] 3.2 Implementar ambos em `TransactionsEntryImpl`: `register` delega a `NavGraphBuilder.transactionsGraph()`, `addTransactionModal` retorna `AddTransactionModal()`
- [ ] 3.3 Rodar `./gradlew check`

## 4. `feature/home/api` — o contrato

- [ ] 4.1 Mover `HomeGraph` de `app/shared/.../ui/screen/home/HomeGraph.kt` para `feature/home/api`
- [ ] 4.2 Mover `HomeChromeConfig`, `HomeChromeController`, `LocalHomeChromeController` e `HomeChromeEffect` de `core/ui/.../ui/screen/home/HomeChrome.kt` para `feature/home/api`; deletar o arquivo em `:core:ui`
- [ ] 4.3 Adicionar `projects.feature.home.api` às dependências de `feature/dashboard/impl` e corrigir os imports de `HomeChromeConfig`/`HomeChromeEffect` em `DashboardScreen`
- [ ] 4.4 Corrigir os imports de `HomeGraph`/`HomeChrome` em `app/shared` (`App.kt`, `AppNavHost.kt`) apontando para `feature:home:api`
- [ ] 4.5 Rodar `./gradlew check` — `HomeChromeStateHolder` ainda não tem casa; deixá-lo temporariamente em `home:api` **não** é aceitável, então ele vai direto para `home:impl` em 5.1 e este passo pode exigir 5.1 junto (ver design, "Ordem de aplicação")

## 5. `feature/home/impl` — a feature

- [ ] 5.1 Criar `HomeChromeStateHolder` e `rememberHomeChromeStateHolder()` em `feature/home/impl` (implementação de `HomeChromeController`)
- [ ] 5.2 Mover `NavigationItem` de `app/shared` para `feature/home/impl`, importando `DashboardRoute` de `dashboard:api` e `TransactionsRoute` de `transactions:api`
- [ ] 5.3 Criar `NavGraphBuilder.homeGraph()` em `feature/home/impl`: `navigation<HomeGraph>(startDestination = DashboardGraph)` registrando `DashboardEntry` e `TransactionsEntry` resolvidos por `KoinPlatform.getKoin()`
- [ ] 5.4 Mover `AppScaffold` de `App.kt` para `feature/home/impl` como `@Composable fun HomeChromeHost(content: @Composable (PaddingValues) -> Unit)`, preservando `updateTransition`, `AnimatedVisibility` da bottom bar, `popUpTo(DashboardRoute) { inclusive = false }` + `launchSingleTop`, o FAB e o `logScreenView(selectedItem.screenName)`
- [ ] 5.5 Trocar `modalManager.show(AddTransactionModal())` por `modalManager.show(transactionsEntry.addTransactionModal())` via `koinInject()`
- [ ] 5.6 Criar `homeModule` (Koin) em `feature/home/impl` e adicioná-lo a `appModules`

## 6. Encolher o `:app:shared`

- [ ] 6.1 Reescrever `App.kt`: remover `AppScaffold`, invocar `HomeChromeHost { padding -> SharedTransitionProvider { AppNavHost(navController, Modifier.padding(padding)) } }`
- [ ] 6.2 Reescrever `AppNavHost.kt`: `startDestination = HomeGraph`, substituir o bloco `navigation<HomeGraph> { dashboardGraph(); transactionsGraph() }` por `homeGraph()`; remover os imports de `dashboard:impl` e `transactions:impl`
- [ ] 6.3 Remover `implementation(projects.feature.dashboard.impl)` e `implementation(projects.feature.transactions.impl)` de `app/shared/build.gradle.kts` **somente se** nenhum outro uso restar; caso contrário manter (o shell ainda os agrega no Koin)
- [ ] 6.4 Tornar `dashboardGraph()` e `transactionsGraph()` `internal` nos respectivos `impl`
- [ ] 6.5 Deletar o pacote `com/neoutils/finsight/ui/screen/home/` de `app/shared`

## 7. Verificação

- [ ] 7.1 `./gradlew check` e `./gradlew allTests` verdes
- [ ] 7.2 Adicionar a `ComposeAppCommonTest` (ou teste novo em `:app:shared`) uma verificação de que `appModules` resolve `DashboardEntry`, `TransactionsEntry` e o `homeModule` — cobre o risco de binding ausente da decisão 5 do design
- [ ] 7.3 `./gradlew :app:android:assembleDebug` e rodar no emulador: bottom bar aparece no dashboard e em transações; some em accounts/categories/creditcards/budgets/recurring/support/report
- [ ] 7.4 Verificar o modo de edição do dashboard: bottom bar e FAB somem e voltam
- [ ] 7.5 Verificar a regressão do commit `47b1fd14`: abrir transações filtradas por um widget do dashboard, tocar na aba Dashboard, confirmar que o dashboard é exibido
- [ ] 7.6 Verificar o FAB: abre o modal de criação de transação
- [ ] 7.7 Verificar as shared transitions do `CreditCardCard` (dashboard → creditcards) e do `SupportUi` — nenhuma fronteira de `NavHost` foi criada, devem continuar funcionando
- [ ] 7.8 `./gradlew :app:desktop:run` e build do iOS via Xcode (`export()` do passo 1.6 só falha lá)

## 8. Documentação

- [ ] 8.1 Atualizar `CLAUDE.md`: `:app:shared` não hospeda mais o `Scaffold`/`HomeGraph`/`NavigationItem`; `:core:ui` não contém mais `HomeChrome`; listar `home` e `dashboard` (com api) nas features
- [ ] 8.2 Atualizar `feature/README.md`: documentar o quarto tipo de acesso cross-feature (`register(NavGraphBuilder)`) e o papel do shell como agregador puro
