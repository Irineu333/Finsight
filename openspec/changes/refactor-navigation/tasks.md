## 1. Módulo `:core:navigation`

- [x] 1.1 Criar `core/navigation/build.gradle.kts` aplicando `finsight.compose.library` e declarando `androidx.navigation.compose`
- [x] 1.2 Registrar `include(":core:navigation")` em `settings.gradle.kts`
- [x] 1.3 Declarar `LocalNavController: staticCompositionLocalOf<NavHostController>` com erro explícito no default
- [x] 1.4 Adicionar `:core:navigation` ao `api(...)` e ao `export(...)` do framework em `app/ios/build.gradle.kts`
- [x] 1.5 Prover `LocalNavController` em `App()` com o `navController` já existente, mantendo o dispatcher no lugar (passo aditivo, projeto compila)

## 2. Migração das features para `navigate` direto

- [x] 2.1 Adicionar `feature:report:api` e `feature:support:api` às dependências de `feature/dashboard/impl/build.gradle.kts`; adicionar `:core:navigation` aos `impl` que navegam (dashboard, transactions, recurring, creditcards)
- [x] 2.2 `dashboard:impl` — substituir `dispatch(NavigationDestination.X)` por `navigate(XRoute)` em `DashboardScreen`, `DashboardComponentContent` e `QuickActionType` (o campo `destination` passa a ser a rota)
- [x] 2.3 `transactions:impl` — substituir os `dispatch` de `ViewOperationModal` e `ViewAdjustmentModal`
- [x] 2.4 `recurring:impl` — substituir os `dispatch` de `ViewRecurringModal`
- [x] 2.5 `creditcards:impl` — substituir o `dispatch` de `CreditCardsScreen`
- [x] 2.6 Verificar que nenhuma referência a `LocalNavigationDispatcher` ou `NavigationDestination` resta fora dos arquivos a remover

## 3. Remoção da camada de tradução

- [x] 3.1 Remover `core/designsystem/.../component/NavigationDispatcher.kt` (interface, `NavigationDestination`, `LocalNavigationDispatcher`, `NavigationDispatcherProvider`)
- [x] 3.2 Remover `app/shared/.../root/AppNavigationDispatcher.kt` e sua chamada em `App()`
- [x] 3.3 Mover `enum NavigationItem` de `core/designsystem/.../BottomNavigationBar.kt` para `:app:shared`, mantendo `BottomNavigationBar` genérico e parametrizado no design system
- [x] 3.4 Confirmar que `:core:designsystem` não nomeia nenhuma feature e não depende de `androidx.navigation`

## 4. Rotas nos módulos corretos

- [x] 4.1 Mover `TransactionTypeNavType` e `TransactionTargetNavType` de `:core:model` para `feature:transactions:api`; declarar `androidx.navigation` na `api` de transactions
- [x] 4.2 Criar `TransactionsRoute(filterType, filterTarget)` em `feature:transactions:api` e remover `HomeRoute.Transactions` do `:app:shared`
- [x] 4.3 Criar `DashboardRoute` em `feature:dashboard:impl` (não criar `feature:dashboard:api`)
- [x] 4.4 Descer `SupportIssueRoute` de `feature:support:api` para `feature:support:impl` e converter `supportGraph` em `navigation<SupportGraph>(startDestination = <lista>)`
- [x] 4.5 Atualizar o `dashboard:impl` para navegar a `TransactionsRoute` em vez de invocar o callback `openTransactions`, removendo o parâmetro de `DashboardScreen`

## 5. Grafos de dashboard e transactions

- [x] 5.1 Criar `NavGraphBuilder.dashboardGraph()` em `feature:dashboard:impl`
- [x] 5.2 Criar `NavGraphBuilder.transactionsGraph()` em `feature:transactions:impl`, com o `typeMap` dos dois `NavType`
- [x] 5.3 Remover de `HomeScreen` os `composable<>` de Dashboard e Transactions

## 6. `NavHost` único

- [x] 6.1 Declarar `navigation<HomeGraph>(startDestination = DashboardGraph)` em `AppNavHost`, aninhando `dashboardGraph()` e `transactionsGraph()`
- [x] 6.2 Mover o `Scaffold` (bottom bar + FAB + `AddTransactionModal`) de `HomeScreen` para `App()`, entre `ModalManagerHost` e `SharedTransitionProvider`
- [x] 6.3 Derivar a visibilidade da chrome de `destination.hierarchy.any { it.hasRoute<HomeGraph>() }` em conjunção com o `HomeChromeConfig` publicado via `LocalHomeChromeController`
- [x] 6.4 Derivar a aba selecionada com `hasRoute<T>()`, substituindo a comparação por `route?.contains(serialName)`
- [x] 6.5 Aplicar `popUpTo(HomeGraph) { saveState = true }`, `restoreState = true` e `launchSingleTop = true` na troca de abas
- [x] 6.6 Remover `HomeScreen.kt`; manter `HomeGraph` como `@Serializable data object` no `:app:shared`
- [x] 6.7 Remover o parâmetro `navController` das assinaturas `NavGraphBuilder.<nome>Graph()`, cujo uso passou a ser `LocalNavController`

## 7. Documentação e verificação

- [x] 7.1 Atualizar `feature/README.md`: a `api` expõe as rotas **externamente navegáveis**; destinos internos residem no `impl`
- [x] 7.2 Atualizar `CLAUDE.md`: `:core:navigation` na lista de cores; remover "dispatcher" da descrição do `:app:shared`
- [ ] 7.3 `./gradlew check` e `./gradlew allTests` verdes
- [ ] 7.4 Verificar manualmente a preservação de scroll ao alternar Transações → Dashboard → Transações
- [ ] 7.5 Verificar manualmente a transição Home → Contas → back (animação da bottom bar) e o modo de edição do dashboard ocultando a chrome
- [ ] 7.6 Verificar o build do framework iOS (`embedAndSignAppleFrameworkForXcode`) com o novo core exportado

## 8. Subgrafo por feature

- [x] 8.1 Envolver todo `<nome>Graph()` em `navigation<<Nome>Graph>(startDestination = <primeira tela>)`
- [x] 8.2 Manter `<Nome>Graph` na `api` apenas quando outro módulo navega até ele (`support`, `report`); os demais residem no `impl`
- [x] 8.3 Apontar `NavigationItem` e o `startDestination` de `HomeGraph` para os subgrafos (`DashboardGraph`, `TransactionsGraph`)
- [x] 8.4 Verificar em runtime que `startDestination` com argumentos (`AccountsRoute()`, `CreditCardsRoute()`, `TransactionsRoute()`) constrói o `NavHost`
