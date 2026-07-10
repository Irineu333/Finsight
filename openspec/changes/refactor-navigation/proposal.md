## Why

A navegação hoje é declarada em `core:designsystem` — o `sealed interface NavigationDestination` enumera as nove telas do produto e o `enum NavigationItem` enumera as duas abas. O design system, camada mais baixa da UI, conhece as features. Isso existe porque `designsystem` não pode depender de `feature:*:api` (inverteria o grafo), então cada rota foi copiada como um destino: `NavigationDestination.Accounts(accountId)` só existe para virar `AccountsRoute(accountId)` no `when` de nove branches do `AppNavigationDispatcher`. Nenhum isolamento é comprado por isso: toda `impl` que hoje despacha já depende da `api` de destino — `DashboardComponentContent` importa `AccountsEntry` de `feature:accounts:api` e, no mesmo arquivo, despacha um destino em vez de importar `AccountsRoute` do mesmíssimo módulo.

O `feature:report` já implementa o desenho correto (`ReportGraph` na `api`, `ReportRoute.Config`/`.Viewer` no `impl`, subgrafo `navigation<ReportGraph>`). Esta mudança generaliza esse padrão e remove a duplicação estrutural que o dispatcher sustenta.

## What Changes

- **Novo `:core:navigation`** contendo apenas `LocalNavController` — o canal de navegação, sem enumerar features.
- **BREAKING (interno):** remoção de `NavigationDispatcher`, `NavigationDestination`, `LocalNavigationDispatcher`, `NavigationDispatcherProvider` e `AppNavigationDispatcher`. As features passam a navegar com `navController.navigate(<Route da api de destino>)`.
- `enum NavigationItem` sai de `:core:designsystem` para `:app:shared`; o composable `BottomNavigationBar` permanece no design system, genérico.
- `TransactionTypeNavType` e `TransactionTargetNavType` saem de `:core:model` (adaptadores de UI no módulo de domínio) para `feature:transactions:api`, junto da rota que os usa — como `PerspectiveTabNavType` já acompanha `ReportRoute.Viewer`.
- `HomeRoute.Transactions(filterType, filterTarget)` — hoje declarada no `:app:shared` — nasce como `TransactionsRoute` em `feature:transactions:api`, pois o dashboard navega até ela.
- `DashboardRoute` nasce em `feature:dashboard:impl`: não é destino de ninguém além do shell, que enxerga `impl`. Não se cria `feature:dashboard:api`.
- `SupportIssueRoute` desce de `feature:support:api` para `feature:support:impl`: só o próprio `SupportScreen` navega até ela.
- `feature:dashboard:impl` e `feature:transactions:impl` passam a expor `NavGraphBuilder.dashboardGraph()` e `NavGraphBuilder.transactionsGraph()`, como as demais features.
- **`NavHost` único.** O `NavHost` aninhado do `HomeScreen` é substituído por `navigation<HomeGraph>(startDestination = DashboardRoute)` no grafo raiz. O `Scaffold` (bottom bar + FAB) sobe para `App()` e sua visibilidade passa a ser derivada do destino atual (`destination.hierarchy.any { it.hasRoute<HomeGraph>() }`) combinada com o `HomeChromeConfig` — que permanece, pois `DashboardScreen` legitimamente esconde a chrome no modo `Editing`.
- `feature/README.md` passa a normatizar "a `api` expõe as rotas **externamente navegáveis**".

Sem mudança de comportamento observável. Deep links, transições de destino e `logScreenView` centralizado ficam fora do escopo — todos se tornam triviais depois, e nenhum é estrutural.

## Capabilities

### New Capabilities
- `navigation`: onde a navegação é declarada e por onde ela trafega — o módulo `:core:navigation` e seu conteúdo mínimo, a proibição de enumerar features fora do shell, a regra de que cada feature expõe na `api` apenas seus destinos externamente navegáveis, o grafo por feature e o `NavHost` único com o subgrafo de abas.

### Modified Capabilities
- `module-architecture`: o requisito "Shell compartilhado em :app:shared" cita "dispatcher de navegação" como conteúdo do shell — o dispatcher deixa de existir, e o shell passa a hospedar o `Scaffold` e o `NavigationItem`. O requisito "Rotas de navegação declaradas por feature" é refinado: a `api` declara as rotas *externamente navegáveis*, e destinos internos residem no `impl`.

## Impact

**Novo módulo:** `:core:navigation` (KMP + Compose, depende de `androidx.navigation`), incluído em `settings.gradle.kts` e exportado no framework iOS por `:app:ios`.

**Removidos:** `core/designsystem/.../NavigationDispatcher.kt`, `app/shared/.../root/AppNavigationDispatcher.kt`, `app/shared/.../home/HomeScreen.kt` (dissolvido em `App()` + `AppNavHost`).

**Alterados:** `App.kt`, `AppNavHost.kt`, `HomeGraph.kt`, `BottomNavigationBar.kt`; os `impl` de `dashboard`, `transactions`, `recurring`, `creditcards` e `support` (substituição de `dispatch(...)` por `navigate(...)`); `core/model` perde dois `NavType`.

**Dependências Gradle:** `feature:dashboard:impl` ganha `feature:report:api` e `feature:support:api` (as duas únicas apis de destino que ainda não declara). Todas as demais dependências `impl → api` necessárias já existem. `feature:transactions:api` passa a depender de `androidx.navigation` (para `NavType`); e todo `feature:*:api` passa a depender de `:core:navigation`, pelos marcadores `NavRoute`/`NavGraphRoute` (decisão 1b do `design.md`).

**Verificação mecânica:** a convenção `feature.api` admite dependências de projeto `:core:*`; `:core:navigation` se enquadra sem alteração no `build-logic`.

**Risco:** o `NavHost` único é o único ponto com regressão visível possível — a preservação de estado de cada aba, hoje gratuita no `NavHost` aninhado, passa a exigir `saveState`/`restoreState` explícitos no `popUpTo`, e a bottom bar passa a animar em toda navegação global.
