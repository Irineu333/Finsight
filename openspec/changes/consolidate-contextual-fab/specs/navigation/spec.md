## MODIFIED Requirements

### Requirement: Shell de navegação adaptativa unificada
A shell que hospeda a navegação primária e o botão de ação SHALL residir em `feature:shell:impl`, exposta como um composable (`ChromeHost`) que recebe o conteúdo do app como parâmetro e é invocada pelo `App()` do `:app:shared`, permanecendo por fora do `NavHost`. A shell SHALL operar sobre uma primitiva única parametrizada por plataforma, diferindo apenas em (1) quais destinos são membros do seletor e (2) onde o seletor é renderizado. O arranjo SHALL ser adaptativo à largura da janela via `currentWindowAdaptiveInfo().windowSizeClass`: em janelas com largura ≥ Medium (600dp) o seletor SHALL ser uma `NavigationRail` à esquerda, contendo **todos os destinos** do catálogo exceto os `mobileOnly`, com o botão de ação no slot `header`; em janelas mais estreitas o seletor SHALL ser uma bottom bar contendo apenas os destinos `primaryTab`, e os demais destinos permanecem alcançáveis por afordâncias empilhadas (grid). Os destinos do seletor SHALL vir do catálogo único (`NavCatalog`), e a seleção do item ativo SHALL ser determinada por correspondência de tipo da rota (`hasRoute<T>()`) sobre a `hierarchy` do destino, com fallback pelo dono do *start destination* da seção quando o sub-destino não tem rota no catálogo, destacando o item raiz mesmo em sub-destinos. O contrato de chrome — `ChromeConfig`, `ChromeController`, `LocalChromeController` e `ChromeEffect` — SHALL residir em `feature:shell:api`, e sua implementação (`ChromeStateHolder`) em `feature:shell:impl`. O `ChromeConfig` MUST NOT declarar a visibilidade do botão de ação: ele rege o **seletor**, e o botão é regido por `contextual-fab`. A visibilidade do seletor SHALL ser: no rail (desktop), persistente, oculto apenas quando a tela publica `ChromeConfig.ContentOnly`; na bottom bar (mobile), visível quando o destino é `primaryTab` e a tela não publica `ContentOnly`. O botão voltar SHALL ser decidido por cada tela via o helper `isWideWindow()` (`:core:designsystem`) — telas host de seção o ocultam no desktop, sub-features sempre o exibem —, sem estado global de navegação na shell. O que o botão de ação oferece, quando ele aparece e onde ele fica SHALL ser regido por `contextual-fab`; a shell MUST NOT obter entry point algum para montá-lo.

#### Scenario: Desktop usa rail persistente com todas as seções
- **WHEN** a largura da janela é ≥ Medium (600dp)
- **THEN** o seletor é uma `NavigationRail` à esquerda com todos os destinos do catálogo exceto os `mobileOnly`, o botão de ação no `header`, e o rail permanece visível ao navegar entre seções

#### Scenario: Mobile usa bottom bar com as abas primárias
- **WHEN** a largura da janela é < Medium (600dp) e o destino é um `primaryTab`
- **THEN** o seletor é uma bottom bar com apenas os destinos `primaryTab`, e o botão de ação é central, ancorado a ela

#### Scenario: Mobile fora de uma aba oculta a bottom bar
- **WHEN** a largura da janela é < Medium e o destino não é `primaryTab` (ex.: Contas aberta pelo grid)
- **THEN** a bottom bar é ocultada e a tela é exibida em modo empilhado com botão voltar, permanecendo o botão de ação visível se a tela publicar ações

#### Scenario: Tela publica ContentOnly
- **WHEN** a tela em foco publica `ChromeConfig.ContentOnly` via `ChromeEffect`
- **THEN** o seletor (rail ou bottom bar) é ocultado em ambos os form factors, e o botão de ação segue a sua própria regra — presente se a tela publicou ações, ausente se não publicou

#### Scenario: Item selecionado em sub-destino
- **WHEN** o usuário está num sub-destino de uma seção (ex.: detalhe de uma fatura, dentro de Cartões)
- **THEN** o item Cartões permanece destacado no seletor, por correspondência de `hasRoute<T>()` na `hierarchy`

#### Scenario: Redimensionamento cruza o breakpoint
- **WHEN** a janela é redimensionada cruzando o breakpoint de Medium
- **THEN** o seletor alterna entre rail e bottom bar preservando o `NavController` e as pilhas, pois apenas o layout muda e não o `NavHost`

#### Scenario: Shell inspecionado no :app:shared
- **WHEN** o `App()` do `:app:shared` é inspecionado
- **THEN** ele não contém `Scaffold`, bottom bar, `NavigationRail`, `FloatingActionButton` nem lógica de visibilidade/seleção — apenas a invocação do composable de shell de `feature:shell:impl`
