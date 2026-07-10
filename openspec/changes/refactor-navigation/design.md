## Context

A navegação atual tem três camadas onde deveria ter uma:

```
feature/*/api          core:designsystem              app:shared
  AccountsRoute   ←──  NavigationDestination.Accounts  ←── when(9 branches)
  (a rota real)        (a cópia)                            (o tradutor)
```

`NavigationDestination` nasceu de uma restrição legítima: `core:designsystem` não pode depender de `feature:*:api` sem inverter o grafo de módulos. A solução foi copiar cada rota como um destino e traduzir no shell. O efeito colateral é que o design system passou a enumerar as features — e o `enum NavigationItem` do `BottomNavigationBar` faz o mesmo com as abas.

O que o dispatcher realmente resolve não é isolamento de tipos, e sim **acesso ao canal**: um composable dentro de `dashboard:impl` não tem `NavHostController`, porque só o `NavGraphBuilder` o recebe. A prova está no grafo de dependências: `dashboard:impl` já depende de `accounts:api`, `budgets:api`, `categories:api`, `creditcards:api`, `recurring:api` e `transactions:api` — seis das oito apis para as quais despacha. `DashboardComponentContent` importa `AccountsEntry` de `feature:accounts:api` para abrir um modal e, no mesmo arquivo, despacha `NavigationDestination.Accounts(id)` em vez de importar `AccountsRoute` do mesmo módulo.

`feature:report` é a única feature que já implementa o desenho correto e serve de referência: `ReportsRoute` na `api` (o subgrafo), `ReportRoute.Config`/`.Viewer` no `impl` (os destinos internos), `PerspectiveTabNavType` junto da rota que o consome.

Restrições que a mudança não pode violar:
1. Nenhum módulo `:core:*` enumera as features.
2. A `api` de uma feature expõe apenas seus destinos externamente navegáveis.
3. As regras de dependência do `build-logic` (`api ⊄ api`, `impl ⊄ impl`, `api ⊄ impl`) permanecem verificadas mecanicamente.

## Goals / Non-Goals

**Goals:**
- Remover a enumeração de features de `:core:designsystem`.
- Eliminar a camada de tradução (`NavigationDestination` + `AppNavigationDispatcher`) sem substituí-la por outra abstração.
- Generalizar o padrão do `report` para todas as features: subgrafo por feature, destinos internos no `impl`.
- Unificar os dois `NavHost` em um, tornando o subgrafo de abas alcançável do grafo raiz.
- Corrigir a inversão de camada `Domain ← UI` em `:core:model` (os dois `NavType`).

**Non-Goals:**
- Deep links. Tornam-se possíveis com o `NavHost` único, mas não são declarados aqui.
- Transições de destino (`enterTransition`/`exitTransition`) e uso consistente de `AnimatedVisibilityScopeProvider`.
- `logScreenView` centralizado via `addOnDestinationChangedListener`.
- Navegação adaptativa (navigation rail no desktop).
- Qualquer mudança de comportamento observável pelo usuário.

## Decisions

### 1. `LocalNavController` cru, não uma interface `Navigator`

A alternativa considerada foi um `interface Navigator { fun navigate(route: NavRoute); fun back() }` em `:core:navigation`, com `sealed interface NavRoute` como marcador implementado por todas as rotas.

Rejeitada. O `Navigator` só compra uma coisa sobre o `NavController` cru: impedir que uma `impl` manipule o backstack global (`popUpTo` de um grafo alheio). Em troca, exige interface + implementação + provider + marcador `NavRoute` + vazamento de `NavOptionsBuilder` assim que a primeira feature precisar de `launchSingleTop`. É a mesma indireção que estamos removendo, com outro nome — e o risco concreto de reintroduzir o `NavigationDestination` sob o rótulo de `NavRoute`.

`LocalNavController` é um `staticCompositionLocalOf<NavHostController>`. Uma declaração. As features chamam `navController.navigate(AccountsRoute(id))` com as opções que precisarem.

Consequência aceita: uma `impl` pode manipular o backstack global. É um risco de disciplina, não de arquitetura — e hoje o `NavGraphBuilder` de cada feature já recebe o `NavController` inteiro como parâmetro.

### 2. `:core:navigation` contém uma declaração, e isso é o tamanho certo

Um módulo Gradle para um `staticCompositionLocalOf` parece desproporcional. Duas alternativas foram consideradas:

- **`LocalNavController` em `:core:designsystem`.** Rejeitada: obriga o design system a depender de `androidx.navigation`, e é o módulo do qual estamos justamente extraindo a navegação.
- **`LocalNavController` em `:core:common`.** Rejeitada pelo mesmo motivo — `common` é util/extension/`UiText`, sem Compose Navigation.

`:core:navigation` é o *seam*, não uma camada. Ele passa a premissa "não enumera features" por construção: não há o que enumerar. E é o lugar natural para helpers genéricos de navegação que venham depois (transições padrão, `composable` com `AnimatedVisibilityScopeProvider` embutido) sem que nenhum deles conheça uma feature.

### 3. Os `NavType` acompanham a rota, não o módulo de navegação

`TransactionTypeNavType` e `TransactionTargetNavType` estão em `:core:model` — adaptadores de navegação dentro do módulo de domínio, invertendo a regra `Domain ← UI`.

Movê-los para `:core:navigation` corrigiria a camada mas violaria a premissa 1 pela porta dos fundos: o módulo de navegação passaria a conhecer os parâmetros de uma rota de uma feature específica. Eles vão para `feature:transactions:api`, junto da `TransactionsRoute` que os consome — exatamente como `PerspectiveTabNavType` já vive no `report:impl`, junto de `ReportRoute.Viewer`.

Isso faz `feature:transactions:api` depender de `androidx.navigation`. É uma dependência de biblioteca, não de projeto: a convenção `feature.api` restringe dependências **de projeto** a `:core:*` e não é afetada.

### 4. Onde cada rota nasce — a premissa 2 decide, não a simetria

| Rota | Módulo | Quem navega até ela |
|---|---|---|
| `TransactionsRoute(filterType, filterTarget)` | `transactions:api` | `dashboard:impl` (hoje via callback `openTransactions`) |
| `DashboardRoute` | `dashboard:impl` | ninguém — é o `startDestination` do subgrafo, montado pelo shell |
| `SupportIssueRoute` | `support:impl` (desce da `api`) | apenas `SupportScreen`, do próprio `impl` |
| `ReportRoute.Config` / `.Viewer` | `report:impl` (já correto) | apenas o próprio `impl` |
| demais rotas de `api` | inalteradas | outras features |

A assimetria `TransactionsRoute` na `api` / `DashboardRoute` no `impl` é o que a premissa 2 prescreve, não uma inconsistência. Não se cria `feature:dashboard:api` — o shell enxerga `impl`, e um módulo inteiro para um `data object` que ninguém importa seria complexidade sem consumidor.

Consequência: `feature/README.md` está mal redigido hoje ("a `api` declara as rotas") e passa a dizer "as rotas externamente navegáveis".

### 5. `NavHost` único, abas como `navigation<HomeRoute>`

Hoje há dois `NavHost`: o raiz e um aninhado dentro de `HomeScreen`, com `HomeRoute.Dashboard` e `HomeRoute.Transactions`. Um destino registrado no `NavHost` interno é inalcançável a partir do controller externo — nem o dispatcher nem um deep link conseguem abrir uma aba. O `selectedItem` é derivado por `destination.route?.contains(serialName)`, comparação textual, existindo `hasRoute<T>()`.

```
NavHost(navController, startDestination = HomeRoute) {
    navigation<HomeRoute>(startDestination = DashboardRoute) {
        dashboardGraph()
        transactionsGraph()
    }
    accountsGraph(); creditCardsGraph(); categoriesGraph()
    budgetsGraph(); recurringGraph(); supportGraph(); reportGraph()
}
```

O `Scaffold` sobe para `App()`. `HomeScreen` se dissolve: o que sobra dele é a bottom bar e o FAB, agora ancorados no destino atual.

### 6. `HomeChrome` sobrevive, com um segundo termo

A hipótese inicial era que `HomeChromeConfig`/`HomeChromeStateHolder` existissem apenas porque o `HomeScreen` era um `Scaffold` isolado que precisava se apagar sozinho. Falso: `DashboardScreen` publica `HomeChromeConfig.ContentOnly` quando `uiState is DashboardUiState.Editing`, para esconder bottom bar e FAB no modo de edição do dashboard. É uma necessidade genuína da feature.

O que muda é que a visibilidade deixa de ser um estado único e passa a ser uma conjunção:

```
visível = destino ∈ hierarquia(HomeRoute)  ∧  chromeConfig.isBottomBarVisible
          └─ derivado do NavController ─┘     └─ publicado pela tela ─┘
```

O primeiro termo substitui o `HomeChromeConfig.Default` que `HomeScreen` aplicava manualmente por aba.

## Risks / Trade-offs

**Perda de estado das abas** → O `NavHost` aninhado preserva o estado de cada aba gratuitamente. Com um `NavHost` só, a troca de abas exige `popUpTo(HomeRoute) { saveState = true }` + `restoreState = true` + `launchSingleTop = true`. Sem isso, a `LazyColumn` de transações perde o scroll ao voltar. É o único ponto da mudança com regressão visível, e o primeiro a verificar manualmente.

**Bottom bar animando em navegação global** → Com o `Scaffold` no `App()`, sair do Home para Contas anima o desaparecimento da bottom bar, onde antes o `HomeScreen` inteiro era substituído. O comportamento observado deve ser equivalente, mas a `AnimatedVisibility` da bottom bar passa a reagir a toda navegação. Verificar transição Home → Contas → back.

**Backstack global exposto às features** → Aceito conscientemente (decisão 1). Mitigação: nenhuma. Se surgir abuso, o `Navigator` continua sendo uma extração possível a partir de um `LocalNavController` já centralizado.

**Ordem de aplicação** → Mover os `NavType` de `:core:model` para `feature:transactions:api` quebra a compilação de `app:shared` até que `HomeRoute.Transactions` vire `TransactionsRoute`. As duas mudanças são um único passo, não dois.

**`:core:navigation` no framework iOS** → Precisa entrar no `export()` do `:app:ios`, senão `LocalNavController` fica invisível ao linkar. Fácil de esquecer e o erro só aparece no build do Xcode.

## Migration Plan

Refactor interno, sem migração de dados nem de API pública. A ordem que mantém o projeto compilando entre passos está em `tasks.md`. Rollback é `git revert` — nenhum estado persistido muda.

## Open Questions

Nenhuma bloqueante. Registradas para depois da mudança:

- `creditcards` tem três destinos, todos externos (`CreditCardsRoute`, `InvoiceTransactionsRoute`, `InstallmentsRoute`). Permanecem `composable<>` irmãos no grafo raiz. Se um dia ganharem um destino interno, viram `navigation<>` como o report.
- `NavigationItem.screenName` é analytics dentro do design system. Sai junto com o enum para `:app:shared`, mas o `logScreenView` centralizado continua fora do escopo.
