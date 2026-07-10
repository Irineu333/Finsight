## ADDED Requirements

### Requirement: Módulo de navegação sem enumeração de features
O projeto SHALL prover um módulo `:core:navigation` contendo exclusivamente o canal de navegação — `LocalNavController`, um `staticCompositionLocalOf<NavHostController>` — e os utilitários genéricos de navegação que não referenciem nenhuma feature. O `:core:navigation` MUST NOT declarar rotas, destinos, abas ou qualquer tipo que enumere as features do produto. Nenhum módulo `:core:*` SHALL enumerar as features; essa responsabilidade pertence exclusivamente ao `:app:shared`.

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
Cada feature navegável SHALL expor no seu `impl` uma única função de extensão `NavGraphBuilder.<nome>Graph()` que registra todos os seus destinos. Uma feature com destinos internos SHALL agrupá-los em `navigation<RotaDeEntrada>(startDestination = <destino interno>)`, de modo que sua rota de entrada pública seja o subgrafo. O `:app:shared` SHALL compor o `NavHost` invocando essas funções e MUST NOT registrar destinos de features diretamente.

#### Scenario: Feature com destinos internos
- **WHEN** uma feature possui uma rota de entrada pública e destinos alcançáveis apenas internamente
- **THEN** seu grafo declara `navigation<RotaPublica>(startDestination = <destino interno>)` com os destinos internos aninhados

#### Scenario: Composição do NavHost
- **WHEN** o `AppNavHost` é inspecionado
- **THEN** ele contém apenas chamadas a `<nome>Graph()` e a declaração do subgrafo de abas, sem nenhum `composable<>` de tela de feature

### Requirement: NavHost único com subgrafo de abas
O app SHALL ter exatamente um `NavHost`. As abas SHALL ser declaradas como `navigation<HomeRoute>` no grafo raiz, tendo o dashboard como `startDestination`. A troca de abas SHALL preservar o estado de cada aba via `popUpTo(HomeRoute) { saveState = true }` e `restoreState = true`.

#### Scenario: Retorno a uma aba já visitada
- **WHEN** o usuário navega de Transações para Dashboard e volta para Transações
- **THEN** a posição de rolagem e o estado da aba Transações são preservados

#### Scenario: Navegação direta para uma aba
- **WHEN** um destino do subgrafo de abas é alvo de `navigate` a partir de qualquer ponto do app
- **THEN** a navegação ocorre no `NavHost` raiz, sem necessidade de acesso a um controller aninhado

### Requirement: Chrome do Home derivada do destino e da tela
O `Scaffold` que hospeda a bottom bar e o FAB SHALL residir no composable raiz `App()`. A visibilidade da chrome SHALL ser a conjunção de (1) o destino atual pertencer à hierarquia de `HomeRoute` e (2) o `HomeChromeConfig` publicado pela tela em foco via `HomeChromeEffect`.

#### Scenario: Destino fora das abas
- **WHEN** o destino atual não pertence à hierarquia de `HomeRoute`
- **THEN** bottom bar e FAB não são exibidos, sem que a tela de destino precise declarar nada

#### Scenario: Tela de aba oculta a chrome
- **WHEN** o dashboard entra no modo de edição e publica `HomeChromeConfig.ContentOnly`
- **THEN** bottom bar e FAB são ocultados enquanto o destino permanece dentro de `HomeRoute`

#### Scenario: Aba selecionada
- **WHEN** a bottom bar precisa destacar a aba corrente
- **THEN** a aba é determinada por correspondência de tipo da rota do destino (`hasRoute<T>()`), e não por comparação textual do nome do destino
