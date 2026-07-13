## MODIFIED Requirements

### Requirement: Chrome do Home derivada do destino e da tela
A chrome que hospeda a navegação primária e o FAB SHALL residir em `feature:home:impl`, exposta como um composable `HomeChromeHost` que recebe o conteúdo do app como parâmetro e é invocada pelo `App()` do `:app:shared`. A chrome SHALL permanecer por fora do `NavHost`. O arranjo da chrome SHALL ser adaptativo à largura da janela, medida pela API oficial do Material3 Adaptive (`currentWindowAdaptiveInfo().windowSizeClass`): em janelas com largura ≥ Medium (600dp) a navegação primária SHALL ser uma `NavigationRail` à esquerda com o FAB no seu slot `header`; em janelas mais estreitas a chrome SHALL ser um `Scaffold` com a bottom bar e o FAB central (`FabPosition.Center`). Ambos os arranjos SHALL reutilizar a mesma lista de abas (`NavigationItem`), a mesma seleção e o mesmo callback de navegação. A visibilidade da chrome SHALL ser a conjunção de (1) o destino atual pertencer à hierarquia de `HomeGraph` e (2) o `HomeChromeConfig` publicado pela tela em foco via `HomeChromeEffect`, aplicando-se igualmente ao rail e à bottom bar. O contrato de chrome — `HomeChromeConfig`, `HomeChromeController`, `LocalHomeChromeController` e `HomeChromeEffect` — SHALL residir em `feature:home:api`, e sua implementação (`HomeChromeStateHolder`) em `feature:home:impl`. A ação primária do FAB SHALL ser obtida por entry point e MUST NOT instanciar um modal de outro `impl`.

#### Scenario: Janela larga usa rail lateral
- **WHEN** a largura da janela é ≥ Medium (600dp) e o destino pertence a `HomeGraph`
- **THEN** a navegação primária é exibida como `NavigationRail` na lateral esquerda, com o FAB no slot `header`, e o conteúdo ocupa o espaço à direita

#### Scenario: Janela estreita usa bottom bar
- **WHEN** a largura da janela é < Medium (600dp) e o destino pertence a `HomeGraph`
- **THEN** a navegação primária é exibida como bottom bar com o FAB central, como no comportamento anterior

#### Scenario: Redimensionamento cruza o breakpoint
- **WHEN** a janela é redimensionada cruzando o breakpoint de Medium
- **THEN** a chrome alterna entre rail e bottom bar preservando o `NavController` e a pilha de navegação, pois apenas o layout muda e não o `NavHost`

#### Scenario: Destino fora das abas
- **WHEN** o destino atual não pertence à hierarquia de `HomeGraph`
- **THEN** nem rail nem bottom bar nem FAB são exibidos, sem que a tela de destino precise declarar nada

#### Scenario: Tela de aba oculta a chrome
- **WHEN** o dashboard entra no modo de edição e publica `HomeChromeConfig.ContentOnly`
- **THEN** a navegação primária (rail ou bottom bar) e o FAB são ocultados enquanto o destino permanece dentro de `HomeGraph`, qualquer que seja a largura

#### Scenario: Aba selecionada
- **WHEN** a navegação primária precisa destacar a aba corrente
- **THEN** a aba é determinada por correspondência de tipo da rota do destino (`hasRoute<T>()`), e não por comparação textual do nome do destino, igualmente no rail e na bottom bar

#### Scenario: Ação primária do FAB
- **WHEN** o usuário toca no FAB do Home
- **THEN** `home:impl` obtém o modal de criação de transação por `TransactionsEntry` e o exibe via `ModalManager`, sem importar nada de `transactions:impl`

#### Scenario: Shell inspecionado
- **WHEN** o `App()` do `:app:shared` é inspecionado
- **THEN** ele não contém `Scaffold`, `BottomNavigationBar`, `NavigationRail`, `FloatingActionButton` nem lógica de visibilidade ou de adaptação de chrome — apenas a invocação de `HomeChromeHost`
