# navigation Specification

## Purpose
TBD - created by syncing change refactor-navigation. Update Purpose after archive.
## Requirements
### Requirement: Módulo de navegação sem enumeração de features
O projeto SHALL prover um módulo `:core:navigation` contendo exclusivamente o canal de navegação — `LocalNavController`, um `staticCompositionLocalOf<NavHostController>` —, os marcadores `NavRoute` e `NavGraphRoute : NavRoute`, e os utilitários genéricos de navegação que não referenciem nenhuma feature. O `:core:navigation` MUST NOT declarar rotas, destinos, abas ou qualquer tipo que enumere as features do produto. Nenhum módulo `:core:*` SHALL enumerar as features; essa responsabilidade pertence exclusivamente ao `:app:shared`.

#### Scenario: Nova feature integrada ao app
- **WHEN** uma nova feature passa a ser navegável
- **THEN** nenhum arquivo de `:core:navigation` ou `:core:designsystem` é alterado

#### Scenario: Adaptador de navegação específico de feature
- **WHEN** uma rota precisa de um `NavType` customizado para um parâmetro (ex.: `Transaction.Type` em `TransactionsRoute`)
- **THEN** o `NavType` reside no mesmo módulo da rota que o consome, e não em `:core:navigation` nem em `:core:model`

#### Scenario: Design system inspecionado
- **WHEN** `:core:designsystem` é inspecionado
- **THEN** ele não contém `NavigationDestination`, `NavigationItem` nem qualquer tipo que nomeie uma feature, e não depende de `androidx.navigation`

### Requirement: Navegação cross-feature pelo NavController
As features SHALL navegar chamando `navigate` no `NavHostController` obtido de `LocalNavController`, passando a rota declarada na `api` da feature de destino. MUST NOT existir uma camada de indireção que espelhe as rotas em um tipo paralelo de destinos.

#### Scenario: Feature navega para tela de outra feature
- **WHEN** `dashboard:impl` precisa abrir a tela de contas
- **THEN** ele obtém o `NavHostController` de `LocalNavController` e chama `navigate(AccountsRoute(accountId))`, importando a rota de `feature:accounts:api`

#### Scenario: Canal indisponível
- **WHEN** um composable que navega é usado fora de uma árvore que proveja `LocalNavController`
- **THEN** a composição falha com erro explícito indicando a ausência do `NavHostController`

### Requirement: Exposição apenas dos destinos externamente navegáveis
A `api` de uma feature SHALL declarar somente as rotas que outro módulo navega. Rotas alcançáveis exclusivamente de dentro do próprio `impl` SHALL residir no `impl`. Uma feature que não é destino de nenhum outro módulo MUST NOT criar um módulo `api` apenas para hospedar sua rota.

#### Scenario: Destino interno de uma feature
- **WHEN** apenas telas do próprio `impl` navegam até uma rota (ex.: o detalhe de uma issue, alcançado somente pela lista de issues)
- **THEN** a rota é declarada no `impl` e não aparece na `api`

#### Scenario: Destino externo de uma feature
- **WHEN** outra feature navega até uma tela com parâmetros (ex.: `dashboard` abre transações filtradas)
- **THEN** a rota é declarada na `api` da feature de destino, com os `NavType` que seus parâmetros exigirem

#### Scenario: Feature que ninguém navega
- **WHEN** uma feature é alcançada apenas como `startDestination` montado pelo shell
- **THEN** sua rota reside no `impl` e nenhum módulo `api` é criado para ela

### Requirement: Grafo de navegação provido por cada feature
Cada feature navegável SHALL expor no seu `impl` uma única função de extensão `NavGraphBuilder.<nome>Graph()` que registra todos os seus destinos, obtendo o `NavHostController` de `LocalNavController` e MUST NOT recebê-lo como parâmetro. Essa função SHALL agrupar seus destinos em `navigation<NomeGraph>(startDestination = <primeira tela>)`, sem exceção — uma feature de tela única também declara seu subgrafo. Toda rota que nomeia um nó de grafo SHALL ser nomeada `<Nome>Graph` e implementar `NavGraphRoute`; rotas que nomeiam uma tela SHALL ser nomeadas `<Nome>Route` e implementar `NavRoute`. Um campo que armazena uma rota MUST NOT ser tipado como `Any`. O `<Nome>Graph` SHALL residir na `api` apenas quando outro módulo navega até ele; caso contrário reside no `impl`, junto da extensão. O `:app:shared` SHALL compor o `NavHost` invocando essas funções e MUST NOT registrar destinos de features diretamente.

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
- **THEN** ele contém apenas chamadas a `<nome>Graph()` e a declaração do subgrafo de abas, sem nenhum `composable<>` de tela de feature

### Requirement: NavHost único com subgrafo de abas
O app SHALL ter exatamente um `NavHost`. As abas SHALL ser declaradas como `navigation<HomeGraph>` no grafo raiz, tendo o dashboard como `startDestination`. A troca de abas SHALL usar `popUpTo(DashboardRoute) { inclusive = false }` e `launchSingleTop = true`, mantendo o dashboard como raiz da pilha das abas. A troca de abas MUST NOT usar `saveState`/`restoreState`: o dashboard empilha destinos de outras abas (transações filtradas por um widget), e o estado salvo de uma aba passaria a conter destinos de outra.

#### Scenario: Retorno ao dashboard a partir de um destino empilhado
- **WHEN** o usuário abre transações filtradas por um widget do dashboard e toca na aba Dashboard
- **THEN** a pilha é desempilhada até o dashboard e ele é exibido

#### Scenario: Voltar de um destino empilhado
- **WHEN** o usuário abre transações filtradas por um widget do dashboard e aciona o voltar
- **THEN** o dashboard é exibido, pois permanece na pilha abaixo do destino empilhado

#### Scenario: Navegação direta para uma aba
- **WHEN** um destino do subgrafo de abas é alvo de `navigate` a partir de qualquer ponto do app
- **THEN** a navegação ocorre no `NavHost` raiz, sem necessidade de acesso a um controller aninhado

### Requirement: Chrome do Home derivada do destino e da tela
O `Scaffold` que hospeda a bottom bar e o FAB SHALL residir no composable raiz `App()`. A visibilidade da chrome SHALL ser a conjunção de (1) o destino atual pertencer à hierarquia de `HomeGraph` e (2) o `HomeChromeConfig` publicado pela tela em foco via `HomeChromeEffect`.

#### Scenario: Destino fora das abas
- **WHEN** o destino atual não pertence à hierarquia de `HomeGraph`
- **THEN** bottom bar e FAB não são exibidos, sem que a tela de destino precise declarar nada

#### Scenario: Tela de aba oculta a chrome
- **WHEN** o dashboard entra no modo de edição e publica `HomeChromeConfig.ContentOnly`
- **THEN** bottom bar e FAB são ocultados enquanto o destino permanece dentro de `HomeGraph`

#### Scenario: Aba selecionada
- **WHEN** a bottom bar precisa destacar a aba corrente
- **THEN** a aba é determinada por correspondência de tipo da rota do destino (`hasRoute<T>()`), e não por comparação textual do nome do destino
