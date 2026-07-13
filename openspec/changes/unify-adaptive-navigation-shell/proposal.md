## Why

O rail lateral introduzido pela change `adaptive-layout` expõe só 2 itens (Dashboard, Transactions) numa janela larga, desperdiçando a coluna vertical, enquanto as 7 features principais (Contas, Cartões, Categorias, Orçamentos, Recorrentes, Relatórios, Parcelas) só são alcançáveis caçando o grid de quick actions dentro do Dashboard. A causa é estrutural: o `HomeGraph` trata Dashboard/Transactions como abas especiais e a chrome é gated por `isHome`, então navegar para uma feature **oculta** a própria navegação. No desktop, o padrão correto é uma sidebar persistente que alterna entre todas as seções — o que exige repensar a topologia de navegação, não só o layout.

## What Changes

- **Uma única primitiva de navegação, parametrizada por plataforma.** Navegar para um **item do seletor** preserva o back stack da seção (`popUpTo(start){saveState}; restoreState; launchSingleTop`); navegar para qualquer outro destino empilha normalmente com botão voltar. Mobile e desktop rodam a mesma lógica, diferindo só em (a) quais destinos são membros do seletor e (b) onde o seletor aparece.
  - **Mobile**: seletor = `{Dashboard, Transactions}` numa bottom bar; demais features abrem empilhadas com voltar (como hoje). Grid de quick actions permanece.
  - **Desktop (≥600dp)**: seletor = **todos os graphs** (menos Support) num rail persistente; sub-destinos de cada seção navegam **dentro** do graph, com voltar apenas para eles. O rail **substitui** o grid.
- **BREAKING (interno)**: `HomeGraph` é dissolvido. Dashboard e Transactions viram graphs top-level (`dashboardGraph()`, `transactionsGraph()`) irmãos das demais features no `AppNavHost` achatado; `startDestination` passa a `DashboardGraph`. A troca de abas deixa de usar `popUpTo(DashboardRoute){inclusive=false}` e passa a usar back stacks por seção (`saveState`/`restoreState`).
- **`feature:home` é renomeada para `feature:shell`** e muda de papel: deixa de "agrupar as 2 abas" e passa a ser a **shell de navegação adaptativa**, dona da chrome (rail/bottom bar/FAB) e do catálogo de destinos. Como shell precisa alternar entre todos os graphs, `shell:impl` passa a depender de todas as `feature:*:api`.
- **Fonte única de destinos.** Um catálogo (`icon`, `labelRes`, `route`, `primaryTab`, `mobileOnly`) substitui os enums `NavigationItem` (2 itens, em `home:impl`) e `QuickActionType` (8 itens, em `dashboard:impl`). Rail, bottom bar e grid **projetam** dessa lista. O grid do Dashboard consome o catálogo via Koin.
- **Contrato de chrome e catálogo em `shell:api`.** `HomeChromeConfig`/`Controller`/`LocalController`/`Effect` e o novo tipo de catálogo migram de `feature:home:api` para `feature:shell:api` (rename do módulo). Preserva o status quo (features já dependem de `home:api` para publicar chrome), sem descer para `:core`.
- **Botão voltar derivado do back stack**: exibido quando `(mobile e fora de uma aba)` **ou** `(profundidade da seção > 1)`. Raiz de qualquer graph no desktop não mostra voltar (o rail é a navegação).
- **Support** permanece mobile-only: fora do rail (desktop), presente no grid (mobile).

## Capabilities

### New Capabilities
<!-- Nenhuma capability nova; o comportamento evolui capacidades existentes. -->

### Modified Capabilities
- `navigation`: a topologia de abas muda de "`NavHost` com subgrafo `HomeGraph` e `popUpTo` sem `saveState`" para "`NavHost` achatado com back stacks por seção"; a "Chrome do Home" vira "Shell de navegação adaptativa unificada" (rail = todos os graphs, bottom bar = 2 abas, mesma primitiva); o contrato de chrome passa de `feature:home:api` para `feature:shell:api` (rename).
- `platform-adaptive-features`: o rail passa a ser um ponto de entrada navegável adicional que também SHALL ocultar features mobile-only (Support); o grid de quick actions passa a ser afordância **mobile-only**.
- `module-architecture`: `feature:home` renomeada para `feature:shell` com papel de shell de navegação (não mais "uma feature como qualquer outra"); `shell:impl` depende de todas as `feature:*:api`; `:app:shared` referencia `DashboardGraph` (não mais `HomeGraph`) como `startDestination`.
- `feature-entry-points`: Dashboard e Transactions deixam de ser hospedados por `home:impl`; seus `dashboardGraph()`/`transactionsGraph()` (que já existem) passam a ser invocados direto pelo `AppNavHost`, como as demais features; a shell não hospeda grafos de outras features.

## Impact

- **Módulos**: `feature/home/*` → `feature/shell/*` (rename de diretório, pacote, módulo Gradle e Koin). `feature:shell:api` herda o contrato de chrome + o tipo/interface do catálogo (só referencia `:core`). `feature:shell:impl` ganha dependência de todas as `feature:*:api` (para construir as rotas concretas do catálogo).
- **`:app:shared`**: `AppNavHost` achatado (`homeGraph()`/`HomeGraph` removidos; `dashboardGraph()`/`transactionsGraph()` chamados direto como as demais features; `startDestination = DashboardGraph`); `App()` invoca a shell renomeada. Continua sem chrome, sem `Scaffold`, sem rotas — regra preservada.
- **`feature:dashboard:impl`**: `QuickActionType` removido; grid alimentado pelo catálogo via Koin; `DashboardGraph`/`dashboardGraph()` passa a top-level.
- **`feature:transactions:impl`**: ganha `transactionsGraph()` top-level.
- **`:core:designsystem`**: `NavigationRailBar`/`BottomNavigationBar` reutilizados; possível novo estado de seleção derivado do catálogo.
- **Ícones**: as 7 features passam a precisar de ícone no rail (hoje `QuickActionType` não tem) — decisão de produto/UX.
- **iOS**: `:app:ios` exporta `feature:*:api`; a lista de exports troca `feature:home:api` por `feature:shell:api` (rename).
- **Testes**: `AppModulesTest` e quaisquer testes que referenciem `HomeGraph`/`homeGraph()`/`NavigationItem`.
