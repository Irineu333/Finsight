# navigation Specification

## Purpose
TBD - created by syncing change refactor-navigation. Update Purpose after archive.
## Requirements
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

### Requirement: Navegação cross-feature pelo NavController
As features SHALL navegar chamando `navigate` no `NavHostController` obtido de `LocalNavController`, passando a rota declarada na `api` da feature de destino. MUST NOT existir uma camada de indireção que espelhe as rotas em um tipo paralelo de destinos.

#### Scenario: Feature navega para tela de outra feature
- **WHEN** `dashboard:impl` precisa abrir a tela de contas
- **THEN** ele obtém o `NavHostController` de `LocalNavController` e chama `navigate(AccountsRoute(accountId))`, importando a rota de `feature:accounts:api`

#### Scenario: Canal indisponível
- **WHEN** um composable que navega é usado fora de uma árvore que proveja `LocalNavController`
- **THEN** a composição falha com erro explícito indicando a ausência do `NavHostController`

### Requirement: Exposição apenas dos destinos externamente navegáveis
A `api` de uma feature SHALL declarar somente as rotas que outro módulo navega. Rotas alcançáveis exclusivamente de dentro do próprio `impl` SHALL residir no `impl`. Uma feature que não é destino de nenhum outro módulo MUST NOT criar um módulo `api` apenas para hospedar sua rota. Quando um módulo passa a navegar até uma rota antes interna, a rota SHALL ser promovida à `api` da feature dona, criando o módulo `api` se ele ainda não existir.

#### Scenario: Destino interno de uma feature
- **WHEN** apenas telas do próprio `impl` navegam até uma rota (ex.: o detalhe de uma issue, alcançado somente pela lista de issues)
- **THEN** a rota é declarada no `impl` e não aparece na `api`

#### Scenario: Destino externo de uma feature
- **WHEN** outra feature navega até uma tela com parâmetros (ex.: `dashboard` abre transações filtradas)
- **THEN** a rota é declarada na `api` da feature de destino, com os `NavType` que seus parâmetros exigirem

#### Scenario: Feature que ninguém navega
- **WHEN** uma feature é alcançada apenas como `startDestination` montado por outro módulo, e nenhum módulo referencia sua rota
- **THEN** sua rota reside no `impl` e nenhum módulo `api` é criado para ela

#### Scenario: Rota interna promovida por um novo consumidor
- **WHEN** uma feature sem módulo `api` passa a ter sua rota referenciada por outro módulo (ex.: a bottom bar de `home:impl` navega até `DashboardRoute`)
- **THEN** o módulo `api` da feature é criado e a rota é movida para ele

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

