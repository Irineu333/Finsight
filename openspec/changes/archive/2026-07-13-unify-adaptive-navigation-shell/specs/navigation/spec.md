## REMOVED Requirements

### Requirement: NavHost único com subgrafo de abas
**Reason**: O subgrafo `HomeGraph` existia apenas para agrupar Dashboard e Transactions e permitir `popUpTo(DashboardRoute)` na troca de abas. Com o `AppNavHost` achatado, o agrupamento deixa de fazer sentido. Substituído por "NavHost único achatado com troca de seção normalizada".
**Migration**: `HomeGraph`/`homeGraph()` são removidos; `dashboardGraph()` e `transactionsGraph()` (que já existem) passam a ser invocados direto pelo `AppNavHost`; `startDestination` passa a `DashboardGraph`. A troca de seção passa a usar `popUpTo(<start>){inclusive=false}; launchSingleTop=true` (sem `saveState`/`restoreState`, incompatíveis com as rotas parametrizadas onipresentes).

### Requirement: Chrome do Home derivada do destino e da tela
**Reason**: A chrome deixa de ser "derivada de pertencer a `HomeGraph`" e passa a ser uma shell de navegação adaptativa com duas topologias parametrizadas (mobile: 2 abas; desktop: todos os graphs). Substituída por "Shell de navegação adaptativa unificada".
**Migration**: A visibilidade deixa de depender de `isHome`. No desktop o rail é persistente (oculto só por `ContentOnly`); no mobile a bottom bar aparece só nos destinos `primaryTab`. A lista de destinos passa a vir do catálogo (`NavCatalog`) em vez do enum `NavigationItem`.

## ADDED Requirements

### Requirement: NavHost único achatado com troca de seção normalizada
O app SHALL ter exatamente um `NavHost`, sem subgrafo agregador de abas. Todos os grafos de feature — incluindo `dashboardGraph()` e `transactionsGraph()` — SHALL ser destinos de primeiro nível invocados diretamente pelo `AppNavHost`, e o `startDestination` SHALL ser `DashboardGraph`. Um `NavHost` aninhado MUST NOT ser introduzido. A navegação SHALL se resolver em dois casos: **(1)** selecionar um **item do seletor** (rail no desktop, bottom bar no mobile) SHALL usar `popUpTo(<start destination do host>){ inclusive = false }; launchSingleTop = true`, normalizando a pilha para `[Dashboard, seção]` a cada seleção; **(2)** navegar para qualquer outro destino SHALL ser um `navigate` comum, empilhando na seção corrente. `saveState`/`restoreState` (multiple back stacks) MUST NOT ser usados: a semântica de estado salvo por seção é incompatível com as rotas parametrizadas onipresentes (`AccountsRoute(id)`, `TransactionsRoute(filtro)`, `CreditCardsRoute(id)` — `restoreState` ignora argumentos) e, misturada com o `navigate()` comum de modais compartilhados, tornava o Dashboard inalcançável. Um destino empilhado a partir de um widget (ex.: transações filtradas abertas de um widget do Dashboard) SHALL empilhar **na seção corrente** via `navigate` comum, com o voltar retornando ao Dashboard.

#### Scenario: Seleção do seletor normaliza a pilha
- **WHEN** o usuário navega dentro de uma seção até um sub-destino e depois seleciona outra seção no rail/bottom bar
- **THEN** a pilha é normalizada para `[Dashboard, nova seção]` (`popUpTo(start){inclusive=false}; launchSingleTop`), de modo que o Dashboard permanece sempre alcançável pelo voltar

#### Scenario: Destino empilhado a partir de um widget
- **WHEN** o usuário abre transações filtradas a partir de um widget do Dashboard
- **THEN** o destino empilha na pilha corrente via `navigate` comum e o voltar retorna ao Dashboard

#### Scenario: Contagem de NavHost
- **WHEN** o código do app é inspecionado
- **THEN** existe exatamente uma chamada a `NavHost`, no `:app:shared`, sem nenhum `navigation<HomeGraph>` e sem nenhuma feature declarando um `NavHost` próprio

#### Scenario: Navegação direta para uma seção
- **WHEN** um grafo de feature é alvo de `navigate` a partir de qualquer ponto do app
- **THEN** a navegação ocorre no `NavHost` raiz, sem necessidade de acesso a um controller aninhado

### Requirement: Shell de navegação adaptativa unificada
A shell que hospeda a navegação primária e o FAB SHALL residir em `feature:shell:impl`, exposta como um composable (`ChromeHost`) que recebe o conteúdo do app como parâmetro e é invocada pelo `App()` do `:app:shared`, permanecendo por fora do `NavHost`. A shell SHALL operar sobre uma primitiva única parametrizada por plataforma, diferindo apenas em (1) quais destinos são membros do seletor e (2) onde o seletor é renderizado. O arranjo SHALL ser adaptativo à largura da janela via `currentWindowAdaptiveInfo().windowSizeClass`: em janelas com largura ≥ Medium (600dp) o seletor SHALL ser uma `NavigationRail` à esquerda, contendo **todos os destinos** do catálogo exceto os `mobileOnly`, com o FAB no slot `header`; em janelas mais estreitas o seletor SHALL ser uma bottom bar contendo apenas os destinos `primaryTab`, com o FAB central (`FabPosition.Center`), e os demais destinos permanecem alcançáveis por afordâncias empilhadas (grid). Os destinos do seletor SHALL vir do catálogo único (`NavCatalog`), e a seleção do item ativo SHALL ser determinada por correspondência de tipo da rota (`hasRoute<T>()`) sobre a `hierarchy` do destino, com fallback pelo dono do *start destination* da seção quando o sub-destino não tem rota no catálogo, destacando o item raiz mesmo em sub-destinos. O contrato de chrome — `ChromeConfig`, `ChromeController`, `LocalChromeController` e `ChromeEffect` — SHALL residir em `feature:shell:api`, e sua implementação (`ChromeStateHolder`) em `feature:shell:impl`. A visibilidade da chrome SHALL ser: no rail (desktop), persistente, oculta apenas quando a tela publica `ChromeConfig.ContentOnly`; na bottom bar (mobile), visível quando o destino é `primaryTab` e a tela não publica `ContentOnly`. O botão voltar SHALL ser decidido por cada tela via o helper `isWideWindow()` (`:core:designsystem`) — telas host de seção o ocultam no desktop, sub-features sempre o exibem —, sem estado global de navegação na shell. A ação primária do FAB SHALL ser obtida por entry point e MUST NOT instanciar um modal de outro `impl`.

#### Scenario: Desktop usa rail persistente com todas as seções
- **WHEN** a largura da janela é ≥ Medium (600dp)
- **THEN** o seletor é uma `NavigationRail` à esquerda com todos os destinos do catálogo exceto os `mobileOnly`, o FAB no `header`, e o rail permanece visível ao navegar entre seções

#### Scenario: Mobile usa bottom bar com as abas primárias
- **WHEN** a largura da janela é < Medium (600dp) e o destino é um `primaryTab`
- **THEN** o seletor é uma bottom bar com apenas os destinos `primaryTab` e o FAB central

#### Scenario: Mobile fora de uma aba oculta a bottom bar
- **WHEN** a largura da janela é < Medium e o destino não é `primaryTab` (ex.: Contas aberta pelo grid)
- **THEN** a bottom bar é ocultada e a tela é exibida em modo empilhado com botão voltar

#### Scenario: Tela publica ContentOnly
- **WHEN** a tela em foco publica `ChromeConfig.ContentOnly` via `ChromeEffect`
- **THEN** o seletor (rail ou bottom bar) e o FAB são ocultados em ambos os form factors

#### Scenario: Item selecionado em sub-destino
- **WHEN** o usuário está num sub-destino de uma seção (ex.: detalhe de uma fatura, dentro de Cartões)
- **THEN** o item Cartões permanece destacado no seletor, por correspondência de `hasRoute<T>()` na `hierarchy`

#### Scenario: Redimensionamento cruza o breakpoint
- **WHEN** a janela é redimensionada cruzando o breakpoint de Medium
- **THEN** o seletor alterna entre rail e bottom bar preservando o `NavController` e as pilhas, pois apenas o layout muda e não o `NavHost`

#### Scenario: Shell inspecionado no :app:shared
- **WHEN** o `App()` do `:app:shared` é inspecionado
- **THEN** ele não contém `Scaffold`, bottom bar, `NavigationRail`, `FloatingActionButton` nem lógica de visibilidade/seleção — apenas a invocação do composable de shell de `feature:shell:impl`

## MODIFIED Requirements

### Requirement: Módulo de navegação sem enumeração de features
O projeto SHALL prover um módulo `:core:navigation` contendo exclusivamente o canal de navegação — `LocalNavController`, um `staticCompositionLocalOf<NavHostController>` —, os marcadores `NavRoute` e `NavGraphRoute : NavRoute`, e os utilitários genéricos de navegação que não referenciem nenhuma feature. O `:core:navigation` MUST NOT declarar rotas, destinos, abas ou qualquer tipo que enumere as features do produto. Nenhum módulo `:core:*` SHALL enumerar as features nem declarar tipos que nomeiem uma feature; a enumeração dos destinos do seletor pertence ao catálogo concreto de `feature:shell:impl`, e a agregação dos grafos e dos módulos Koin pertence ao `:app:shared`.

#### Scenario: Nova feature integrada ao app
- **WHEN** uma nova feature passa a ser navegável
- **THEN** nenhum arquivo de `:core:navigation` ou `:core:designsystem` é alterado

#### Scenario: Adaptador de navegação específico de feature
- **WHEN** uma rota precisa de um `NavType` customizado para um parâmetro (ex.: `Transaction.Type` em `TransactionsRoute`)
- **THEN** o `NavType` reside no mesmo módulo da rota que o consome, e não em `:core:navigation` nem em `:core:model`

#### Scenario: Design system inspecionado
- **WHEN** `:core:designsystem` é inspecionado
- **THEN** ele não contém `NavigationDestination`, `NavigationItem` nem qualquer tipo que nomeie uma feature, e não depende de `androidx.navigation`

#### Scenario: Core de UI inspecionado
- **WHEN** `:core:ui` é inspecionado
- **THEN** ele não contém `ChromeConfig`, `ChromeEffect`, `LocalChromeController` nem qualquer outro tipo que nomeie uma feature; esses tipos residem em `feature:shell:api`

### Requirement: Grafo de navegação provido por cada feature
Cada feature navegável SHALL expor no seu `impl` uma única função de extensão `NavGraphBuilder.<nome>Graph()` que registra todos os seus destinos, obtendo o `NavHostController` de `LocalNavController` e MUST NOT recebê-lo como parâmetro. Essa função SHALL agrupar seus destinos em `navigation<<Name>Graph>(startDestination = <primeira tela>)`, sem exceção — uma feature de tela única também declara seu subgrafo. Toda rota que nomeia um nó de grafo SHALL ser nomeada `<Nome>Graph` e implementar `NavGraphRoute`; rotas que nomeiam uma tela SHALL ser nomeadas `<Nome>Route` e implementar `NavRoute`. Um campo que armazena uma rota MUST NOT ser tipado como `Any`. O `<Nome>Graph` SHALL residir na `api` apenas quando outro módulo navega até ele; caso contrário reside no `impl`, junto da extensão. O `:app:shared` SHALL compor o `NavHost` invocando as funções de grafo de **todas** as features de primeiro nível — incluindo `dashboardGraph()` e `transactionsGraph()` — e MUST NOT registrar destinos de features diretamente nem declarar `navigation<>`. O padrão de uma feature hospedar o grafo de outra via `register()` no entry point permanece disponível, mas nenhuma feature de primeiro nível é hospedada por outra: Dashboard e Transactions são invocados diretamente pelo `AppNavHost`, como as demais.

#### Scenario: Feature de tela única
- **WHEN** uma feature possui um único destino (ex.: `budgets`)
- **THEN** seu grafo ainda declara `navigation<BudgetsGraph>(startDestination = BudgetsRoute)`, e `BudgetsGraph` reside no `impl` porque nenhum outro módulo navega até ele

#### Scenario: Feature com destinos internos
- **WHEN** uma feature possui uma rota de entrada pública e destinos alcançáveis apenas internamente
- **THEN** seu grafo declara `navigation<NomeGraph>(startDestination = <destino interno>)` com os destinos internos aninhados, e `NomeGraph` reside na `api`

#### Scenario: Nome e tipo de uma rota
- **WHEN** uma rota é declarada
- **THEN** ela se chama `<Nome>Graph` e implementa `NavGraphRoute` se for o nó de um subgrafo (`SupportGraph`, `ReportGraph`, `DashboardGraph`), e se chama `<Nome>Route` e implementa `NavRoute` se for uma tela (`AccountsRoute`, `SupportIssueRoute`)

#### Scenario: Rastrear todas as rotas do app
- **WHEN** se quer enumerar as rotas existentes ou os pontos que navegam
- **THEN** basta buscar as implementações de `NavRoute`, pois nenhum campo de rota é tipado como `Any`

#### Scenario: Composição do NavHost
- **WHEN** o `AppNavHost` é inspecionado
- **THEN** ele contém apenas chamadas a `<nome>Graph()` — incluindo `dashboardGraph()` e `transactionsGraph()` — sem nenhum `homeGraph()`, sem nenhum `composable<>` de tela de feature e sem nenhuma declaração de `navigation<>`

#### Scenario: Abas promovidas a grafos de primeiro nível
- **WHEN** o app monta a navegação de Dashboard e Transactions
- **THEN** cada uma expõe seu próprio `dashboardGraph()`/`transactionsGraph()` invocado diretamente pelo `AppNavHost`, sem um subgrafo agregador e sem serem hospedadas por outra feature
