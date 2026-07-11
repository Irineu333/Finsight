## MODIFIED Requirements

### Requirement: Módulo de navegação sem enumeração de features
O projeto SHALL prover um módulo `:core:navigation` contendo exclusivamente o canal de navegação — `LocalNavController`, um `staticCompositionLocalOf<NavHostController>` —, os marcadores `NavRoute` e `NavGraphRoute : NavRoute`, e os utilitários genéricos de navegação que não referenciem nenhuma feature. O `:core:navigation` MUST NOT declarar rotas, destinos, abas ou qualquer tipo que enumere as features do produto. Nenhum módulo `:core:*` SHALL enumerar as features nem declarar tipos que nomeiem uma feature; a enumeração das abas do Home pertence a `feature:home:impl`, e a agregação dos grafos e dos módulos Koin pertence ao `:app:shared`.

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
- **THEN** ele não contém `HomeChromeConfig`, `HomeChromeEffect`, `LocalHomeChromeController` nem qualquer outro tipo que nomeie uma feature; esses tipos residem em `feature:home:api`

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
Cada feature navegável SHALL expor no seu `impl` uma única função de extensão `NavGraphBuilder.<nome>Graph()` que registra todos os seus destinos, obtendo o `NavHostController` de `LocalNavController` e MUST NOT recebê-lo como parâmetro. Essa função SHALL agrupar seus destinos em `navigation<NomeGraph>(startDestination = <primeira tela>)`, sem exceção — uma feature de tela única também declara seu subgrafo. Toda rota que nomeia um nó de grafo SHALL ser nomeada `<Nome>Graph` e implementar `NavGraphRoute`; rotas que nomeiam uma tela SHALL ser nomeadas `<Nome>Route` e implementar `NavRoute`. Um campo que armazena uma rota MUST NOT ser tipado como `Any`. O `<Nome>Graph` SHALL residir na `api` apenas quando outro módulo navega até ele; caso contrário reside no `impl`, junto da extensão. O `:app:shared` SHALL compor o `NavHost` invocando as funções de grafo das features de primeiro nível e MUST NOT registrar destinos de features diretamente. Uma feature que hospeda os grafos de outras features SHALL registrá-los pelo entry point da feature hospedada, sem depender do seu `impl`.

#### Scenario: Feature de tela única
- **WHEN** uma feature possui um único destino (ex.: `budgets`)
- **THEN** seu grafo ainda declara `navigation<BudgetsGraph>(startDestination = BudgetsRoute)`, e `BudgetsGraph` reside no `impl` porque nenhum outro módulo navega até ele

#### Scenario: Feature com destinos internos
- **WHEN** uma feature possui uma rota de entrada pública e destinos alcançáveis apenas internamente
- **THEN** seu grafo declara `navigation<NomeGraph>(startDestination = <destino interno>)` com os destinos internos aninhados, e `NomeGraph` reside na `api`

#### Scenario: Nome e tipo de uma rota
- **WHEN** uma rota é declarada
- **THEN** ela se chama `<Nome>Graph` e implementa `NavGraphRoute` se for o nó de um subgrafo (`SupportGraph`, `ReportGraph`, `HomeGraph`), e se chama `<Nome>Route` e implementa `NavRoute` se for uma tela (`AccountsRoute`, `SupportIssueRoute`)

#### Scenario: Rastrear todas as rotas do app
- **WHEN** se quer enumerar as rotas existentes ou os pontos que navegam
- **THEN** basta buscar as implementações de `NavRoute`, pois nenhum campo de rota é tipado como `Any`

#### Scenario: Composição do NavHost
- **WHEN** o `AppNavHost` é inspecionado
- **THEN** ele contém apenas chamadas a `<nome>Graph()`, incluindo `homeGraph()`, sem nenhum `composable<>` de tela de feature e sem nenhuma declaração de `navigation<>`

#### Scenario: Feature hospeda o grafo de outra feature
- **WHEN** `home:impl` monta o subgrafo de abas com os destinos de `dashboard` e `transactions`
- **THEN** ele obtém `DashboardEntry` e `TransactionsEntry` do Koin e invoca o `register()` de cada um, com o `NavGraphBuilder` fornecido como context parameter pelo receiver implícito do `navigation<>`, sem importar nada de `dashboard:impl` ou `transactions:impl`

### Requirement: NavHost único com subgrafo de abas
O app SHALL ter exatamente um `NavHost`. As abas SHALL ser declaradas como `navigation<HomeGraph>` por `NavGraphBuilder.homeGraph()`, provida por `feature:home:impl` e invocada pelo `AppNavHost`, tendo o dashboard como `startDestination`. `HomeGraph` SHALL residir em `feature:home:api`. Um `NavHost` aninhado MUST NOT ser introduzido para hospedar as abas. A troca de abas SHALL usar `popUpTo(DashboardRoute) { inclusive = false }` e `launchSingleTop = true`, mantendo o dashboard como raiz da pilha das abas. A troca de abas MUST NOT usar `saveState`/`restoreState`: o dashboard empilha destinos de outras abas (transações filtradas por um widget), e o estado salvo de uma aba passaria a conter destinos de outra.

#### Scenario: Retorno ao dashboard a partir de um destino empilhado
- **WHEN** o usuário abre transações filtradas por um widget do dashboard e toca na aba Dashboard
- **THEN** a pilha é desempilhada até o dashboard e ele é exibido

#### Scenario: Voltar de um destino empilhado
- **WHEN** o usuário abre transações filtradas por um widget do dashboard e aciona o voltar
- **THEN** o dashboard é exibido, pois permanece na pilha abaixo do destino empilhado

#### Scenario: Navegação direta para uma aba
- **WHEN** um destino do subgrafo de abas é alvo de `navigate` a partir de qualquer ponto do app
- **THEN** a navegação ocorre no `NavHost` raiz, sem necessidade de acesso a um controller aninhado

#### Scenario: Contagem de NavHost
- **WHEN** o código do app é inspecionado
- **THEN** existe exatamente uma chamada a `NavHost`, no `:app:shared`, e nenhuma feature declara um `NavHost` próprio

### Requirement: Chrome do Home derivada do destino e da tela
O `Scaffold` que hospeda a bottom bar e o FAB SHALL residir em `feature:home:impl`, exposto como um composable `HomeChromeHost` que recebe o conteúdo do app como parâmetro e é invocado pelo `App()` do `:app:shared`. O `Scaffold` SHALL permanecer por fora do `NavHost`. A visibilidade da chrome SHALL ser a conjunção de (1) o destino atual pertencer à hierarquia de `HomeGraph` e (2) o `HomeChromeConfig` publicado pela tela em foco via `HomeChromeEffect`. O contrato de chrome — `HomeChromeConfig`, `HomeChromeController`, `LocalHomeChromeController` e `HomeChromeEffect` — SHALL residir em `feature:home:api`, e sua implementação (`HomeChromeStateHolder`) em `feature:home:impl`. A ação primária do FAB SHALL ser obtida por entry point e MUST NOT instanciar um modal de outro `impl`.

#### Scenario: Destino fora das abas
- **WHEN** o destino atual não pertence à hierarquia de `HomeGraph`
- **THEN** bottom bar e FAB não são exibidos, sem que a tela de destino precise declarar nada

#### Scenario: Tela de aba oculta a chrome
- **WHEN** o dashboard entra no modo de edição e publica `HomeChromeConfig.ContentOnly`
- **THEN** bottom bar e FAB são ocultados enquanto o destino permanece dentro de `HomeGraph`

#### Scenario: Aba selecionada
- **WHEN** a bottom bar precisa destacar a aba corrente
- **THEN** a aba é determinada por correspondência de tipo da rota do destino (`hasRoute<T>()`), e não por comparação textual do nome do destino

#### Scenario: Ação primária do FAB
- **WHEN** o usuário toca no FAB do Home
- **THEN** `home:impl` obtém o modal de criação de transação por `TransactionsEntry` e o exibe via `ModalManager`, sem importar nada de `transactions:impl`

#### Scenario: Shell inspecionado
- **WHEN** o `App()` do `:app:shared` é inspecionado
- **THEN** ele não contém `Scaffold`, `BottomNavigationBar`, `FloatingActionButton` nem lógica de visibilidade de chrome — apenas a invocação de `HomeChromeHost`
