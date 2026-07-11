## 1. Esqueleto dos módulos

Cria os módulos vazios e os registra. Ao fim deste grupo o projeto compila igual a antes — nada foi movido.

- [x] 1.1 Criar `feature/dashboard/api/build.gradle.kts` com `id("finsight.feature.api")`, dependências `projects.core.navigation` + `api(libs.androidx.navigation.compose)` (o `register` expõe `NavGraphBuilder`) e `projects.core.designsystem`
- [x] 1.2 Criar `feature/home/api/build.gradle.kts` com `id("finsight.feature.api")` e dependências `projects.core.navigation`, `projects.core.designsystem`
- [x] 1.3 Criar `feature/home/impl/build.gradle.kts` com `id("finsight.feature.impl")` e dependências `projects.core.{common,model,navigation,designsystem,ui,resources,analytics}` + `projects.feature.home.api`, `projects.feature.dashboard.api`, `projects.feature.transactions.api`
- [x] 1.4 Registrar `:feature:dashboard:api`, `:feature:home:api` e `:feature:home:impl` no `settings.gradle.kts`
- [x] 1.5 Adicionar `projects.feature.dashboard.api` como dependência de `feature/dashboard/impl` e `projects.feature.home.{api,impl}` a `app/shared/build.gradle.kts`
- [x] 1.6 Adicionar `:feature:home:api` e `:feature:dashboard:api` ao `export()` do framework `ComposeApp` em `app/ios/build.gradle.kts`
- [x] 1.7 Rodar `./gradlew check` — deve passar com os módulos vazios

## 2. `dashboard:api` e `DashboardEntry`

- [x] 2.1 Mover `DashboardRoute` de `feature/dashboard/impl/.../ui/screen/dashboard/DashboardRoute.kt` para `feature/dashboard/api`
- [x] 2.2 Criar `DashboardEntry` em `feature/dashboard/api` com `context(builder: NavGraphBuilder) fun register()`
- [x] 2.3 Criar `DashboardEntryImpl` (internal) em `dashboard:impl` delegando a `NavGraphBuilder.dashboardGraph()`; tornar `dashboardGraph()` internal
- [x] 2.4 Registrar `single<DashboardEntry> { DashboardEntryImpl() }` no `dashboardModule`
- [x] 2.5 Rodar `./gradlew check` — `AppNavHost` ainda chama `dashboardGraph()` diretamente, então este passo mantém a extensão pública até 5.2, se necessário

## 3. `TransactionsEntry` ganha grafo e modal

- [x] 3.1 Adicionar `context(builder: NavGraphBuilder) fun register()` e `fun addTransactionModal(): Modal` a `TransactionsEntry` em `feature/transactions/api`
- [x] 3.2 Implementar ambos em `TransactionsEntryImpl`: `register` delega a `NavGraphBuilder.transactionsGraph()`, `addTransactionModal` retorna `AddTransactionModal()`
- [x] 3.3 Rodar `./gradlew check`

## 4. `feature/home/api` — o contrato

- [x] 4.1 Mover `HomeGraph` de `app/shared/.../ui/screen/home/HomeGraph.kt` para `feature/home/api`
- [x] 4.2 Mover `HomeChromeConfig`, `HomeChromeController`, `LocalHomeChromeController` e `HomeChromeEffect` de `core/ui/.../ui/screen/home/HomeChrome.kt` para `feature/home/api`; deletar o arquivo em `:core:ui`
- [x] 4.3 Adicionar `projects.feature.home.api` às dependências de `feature/dashboard/impl` e corrigir os imports de `HomeChromeConfig`/`HomeChromeEffect` em `DashboardScreen`
- [x] 4.4 Corrigir os imports de `HomeGraph`/`HomeChrome` em `app/shared` (`App.kt`, `AppNavHost.kt`) apontando para `feature:home:api`
- [x] 4.5 Rodar `./gradlew check` — `HomeChromeStateHolder` ainda não tem casa; deixá-lo temporariamente em `home:api` **não** é aceitável, então ele vai direto para `home:impl` em 5.1 e este passo pode exigir 5.1 junto (ver design, "Ordem de aplicação")

## 5. `feature/home/impl` — a feature

- [x] 5.1 Criar `HomeChromeStateHolder` e `rememberHomeChromeStateHolder()` em `feature/home/impl` (implementação de `HomeChromeController`)
- [x] 5.2 Mover `NavigationItem` de `app/shared` para `feature/home/impl`, importando `DashboardRoute` de `dashboard:api` e `TransactionsRoute` de `transactions:api`
- [x] 5.3 Criar `NavGraphBuilder.homeGraph()` em `feature/home/impl`: `navigation<HomeGraph>(startDestination = DashboardGraph)` registrando `DashboardEntry` e `TransactionsEntry` resolvidos por `KoinPlatform.getKoin()`
- [x] 5.4 Mover `AppScaffold` de `App.kt` para `feature/home/impl` como `@Composable fun HomeChromeHost(content: @Composable (PaddingValues) -> Unit)`, preservando `updateTransition`, `AnimatedVisibility` da bottom bar, `popUpTo(DashboardRoute) { inclusive = false }` + `launchSingleTop`, o FAB e o `logScreenView(selectedItem.screenName)`
- [x] 5.5 Trocar `modalManager.show(AddTransactionModal())` por `modalManager.show(transactionsEntry.addTransactionModal())` via `koinInject()`
- [x] 5.6 ~~Criar `homeModule` (Koin) em `feature/home/impl` e adicioná-lo a `appModules`~~ **Não feito, por decisão.** O `home` não tem ViewModel, use case nem repositório; o `HomeChromeStateHolder` é um `remember` de composição, não um binding. O módulo sairia vazio. `appModules` não muda.

## 6. Encolher o `:app:shared`

- [x] 6.1 Reescrever `App.kt`: remover `AppScaffold`, invocar `HomeChromeHost { padding -> SharedTransitionProvider { AppNavHost(navController, Modifier.padding(padding)) } }`
- [x] 6.2 Reescrever `AppNavHost.kt`: `startDestination = HomeGraph`, substituir o bloco `navigation<HomeGraph> { dashboardGraph(); transactionsGraph() }` por `homeGraph()`; remover os imports de `dashboard:impl` e `transactions:impl`
- [x] 6.3 Remover `implementation(projects.feature.dashboard.impl)` e `implementation(projects.feature.transactions.impl)` de `app/shared/build.gradle.kts` **somente se** nenhum outro uso restar; caso contrário manter (o shell ainda os agrega no Koin)
- [x] 6.4 Tornar `dashboardGraph()` e `transactionsGraph()` `internal` nos respectivos `impl`
- [x] 6.5 Deletar o pacote `com/neoutils/finsight/ui/screen/home/` de `app/shared`
- [x] 6.6 Mover `App.kt` e `AppNavHost.kt` de `ui/screen/root/` para `ui/`, atualizando o import de `App` em `:app:android`, `:app:desktop` e `:app:ios`. O pacote `screen/` some do shell: ele não hospeda mais nenhuma tela.

## 7. Verificação

- [x] 7.1 `./gradlew check` e `./gradlew allTests` verdes — exceto `linkDebugTest*Ios*`, que **já falhava na `HEAD` limpa** antes desta mudança (erro de linker do toolchain, verificado com `git stash`). Não é regressão desta change.
- [x] 7.2 Adicionar a `ComposeAppCommonTest` (ou teste novo em `:app:shared`) uma verificação de que `appModules` resolve `DashboardEntry`, `TransactionsEntry` e o `homeModule` — cobre o risco de binding ausente da decisão 5 do design
- [x] 7.3 `./gradlew :app:android:assembleDebug` e rodar no emulador: bottom bar aparece no dashboard e em transações; some em accounts/categories/creditcards/budgets/recurring/support/report
- [x] 7.4 Verificar o modo de edição do dashboard: bottom bar e FAB somem e voltam
- [x] 7.5 Verificar a regressão do commit `47b1fd14`: abrir transações filtradas por um widget do dashboard, tocar na aba Dashboard, confirmar que o dashboard é exibido
- [x] 7.6 Verificar o FAB: abre o modal de criação de transação
- [x] 7.7 Verificar as shared transitions do `CreditCardCard` (dashboard → creditcards) — verificado no emulador com um cartão criado para o teste. A do `SupportUi` não foi exercitada (exigiria criar uma issue); é interna ao `supportGraph` e nenhuma fronteira de `NavHost` foi criada.
- [x] 7.8 `./gradlew :app:desktop:run` verde. **Build do iOS via Xcode não executado** — o `export()` do passo 1.6 só é validado lá, e o link do simulador já falha nesta máquina por motivo pré-existente. Pendente de verificação numa máquina com o toolchain iOS sadio.

## 8. Documentação

- [x] 8.1 Atualizar `CLAUDE.md`: `:app:shared` não hospeda mais o `Scaffold`/`HomeGraph`/`NavigationItem`; `:core:ui` não contém mais `HomeChrome`; listar `home` e `dashboard` (com api) nas features
- [x] 8.2 Atualizar `feature/README.md`: documentar o quarto tipo de acesso cross-feature (`context(builder: NavGraphBuilder) fun register()`) e o papel do shell como agregador puro
