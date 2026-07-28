# shared-element-transitions Specification

## Purpose
TBD - created by archiving change fix-credit-card-shared-transition-overlay. Update Purpose after archive.
## Requirements
### Requirement: A promoção a elemento compartilhado pertence ao chamador

Um componente de `:core:ui` MUST NOT decidir por conta própria participar de uma transição compartilhada pela mera presença de `LocalSharedTransitionScope` e `LocalAnimatedVisibilityScope` no ambiente. O módulo que define o componente SHALL expor a promoção como um `Modifier` nomeado, e o chamador SHALL aplicá-lo apenas às instâncias que decidiu promover. A chave da transição SHALL ser derivada da identidade do modelo e ter exatamente um dono, no módulo que define o componente — nenhuma feature SHALL reconstruir a chave.

O `Modifier` de promoção SHALL ser inerte (`Modifier` vazio) quando os scopes de transição não estiverem disponíveis, de modo que o mesmo call site funcione dentro e fora de um `SharedTransitionLayout`.

#### Scenario: Componente renderizado sem promoção
- **WHEN** `CreditCardCard` é renderizado sem que o chamador aplique o `Modifier` de promoção, dentro de um `SharedTransitionLayout` ativo
- **THEN** o cartão não é elevado ao overlay de transição e é desenhado normalmente, clipado por seus containers

#### Scenario: Chave com dono único
- **WHEN** o código de qualquer módulo `feature:*` é inspecionado
- **THEN** a string da chave de transição do cartão de crédito não aparece em nenhum deles; ela existe apenas em `:core:ui`, junto ao componente

#### Scenario: Call site fora de um SharedTransitionLayout
- **WHEN** um chamador aplica o `Modifier` de promoção num contexto sem `LocalSharedTransitionScope` ou sem `LocalAnimatedVisibilityScope`
- **THEN** o `Modifier` é inerte e a renderização ocorre sem erro

### Requirement: No máximo um elemento por chave é promovido em cada tela

Em uma tela que renderiza uma coleção de itens sob a mesma família de chaves — notadamente um `HorizontalPager`, cujo `contentPadding` faz com que as páginas vizinhas sejam compostas —, a tela SHALL promover no máximo o item correspondente à seleção corrente. Itens compostos fora da viewport, ou visíveis apenas parcialmente por efeito de `contentPadding`, MUST NOT ser promovidos.

Esse requisito existe porque um elemento promovido é elevado ao overlay do `SharedTransitionLayout`, onde o clip de seus containers deixa de valer: um item posicionado fora da viewport passaria a ser desenhado por inteiro, em coordenadas arbitrárias, sobre o restante da interface.

#### Scenario: Navegação a partir de um pager com vizinhos compostos
- **WHEN** o usuário toca no cartão corrente do pager da dashboard e navega para a tela de cartões
- **THEN** apenas o cartão tocado é elevado ao overlay; as páginas vizinhas permanecem clipadas pelo pager durante toda a animação

#### Scenario: Tela de destino com seleção
- **WHEN** a tela de cartões é composta com um `selectedCardIndex` resolvido
- **THEN** apenas a página desse índice recebe o `Modifier` de promoção

#### Scenario: Nenhum item fora da viewport aparece durante a transição
- **WHEN** uma transição compartilhada entre dashboard e tela de cartões está em curso, em qualquer largura de janela
- **THEN** nenhum cartão é desenhado fora da área de conteúdo — nem à esquerda, sobre o `NavigationRail`, nem à direita, sobre o painel de detalhe

### Requirement: O chrome da casca é desenhado acima do overlay de transição

O `SharedTransitionLayout` da aplicação SHALL envolver a casca (`ChromeHost`), e não ser instanciado dentro do slot de conteúdo dela. O `NavigationRail`, a barra de navegação inferior e o FAB SHALL declarar-se no overlay do escopo de transição com prioridade acima dos elementos compartilhados. Nenhum elemento compartilhado, qualquer que seja sua trajetória, SHALL ser desenhado sobre o chrome da casca.

O chrome MUST NOT ser tratado como um bloco de prioridade única. A ordem de pintura **entre** seus componentes SHALL ser explícita: o FAB SHALL ficar acima da barra de navegação inferior e do `NavigationRail`. Essa ordem espelha a ordem de placement que o `Scaffold` já pratica fora do overlay — a aparência do chrome MUST NOT depender de haver ou não uma transição compartilhada em curso.

Nenhum participante do overlay SHALL depender da ordem de composição para ser desempatado. Dois participantes com prioridade idêntica são ordenados pela ordem em que seus nós foram anexados, que para os slots do `Scaffold` é a inversa da ordem de desenho normal; portanto participantes que se sobrepõem geometricamente SHALL declarar prioridades distintas.

A área de conteúdo do `AppNavHost` SHALL continuar recebendo o `padding` publicado pela casca; subir o `SharedTransitionLayout` MUST NOT alterar o layout do conteúdo.

#### Scenario: Elemento compartilhado com trajetória fora da área de conteúdo
- **WHEN** um elemento compartilhado é animado com bounds que ultrapassam a área de conteúdo, por qualquer motivo
- **THEN** ele é desenhado por baixo do `NavigationRail`, da barra inferior e do FAB, que permanecem integralmente visíveis

#### Scenario: FAB docked durante uma transição em janela compacta
- **WHEN** o usuário navega entre a dashboard e outra tela em janela compacta, com ao menos um cartão de crédito cadastrado — condições que ativam o overlay enquanto o chrome está visível
- **THEN** o FAB é desenhado por inteiro, sobre a barra de navegação inferior, durante toda a animação e nos dois sentidos da navegação

#### Scenario: Aparência do FAB independe da transição
- **WHEN** a mesma tela é observada com uma transição compartilhada em curso e em repouso
- **THEN** a porção do FAB que se sobrepõe à barra inferior é desenhada de forma idêntica nos dois momentos

#### Scenario: Layout do conteúdo preservado
- **WHEN** o app é composto após o `SharedTransitionProvider` passar a envolver o `ChromeHost`
- **THEN** o conteúdo do `AppNavHost` ocupa a mesma área que ocupava antes, respeitando o `padding` da casca

#### Scenario: Superfícies acima do chrome
- **WHEN** um `ModalBottomSheet`, um painel de detalhe ou um `DropdownMenu` é exibido
- **THEN** ele continua sendo desenhado acima do chrome da casca, sem regressão de ordenação

### Requirement: A pilha de prioridades do overlay tem um dono único e é declarada, nunca herdada

A ordem de pintura no overlay de transição SHALL existir escrita como uma escala nomeada, com exatamente um dono, no módulo que já é dono do vocabulário do overlay. Nenhum módulo que participa do overlay SHALL redefinir a escala nem inferir sua posição a partir da posição dos outros.

A escala SHALL nomear seus níveis pelo papel do participante — o elemento compartilhado, o chrome de navegação, o FAB —, nunca pelo valor numérico. Os valores concretos são detalhe interno da escala.

Todo participante do overlay SHALL declarar seu nível explicitamente no call site, inclusive o que ocupa o nível mais baixo. Herdar a posição do valor default do framework MUST NOT ser aceito como declaração: uma prioridade que ninguém escreveu não sobrevive à chegada do próximo participante.

O módulo dono da escala SHALL ser visível de todos os módulos que participam do overlay, sem que nenhum deles precise nomear os demais.

#### Scenario: Escala inspecionada em um único lugar
- **WHEN** o código é inspecionado em busca dos valores de prioridade do overlay
- **THEN** eles aparecem em um único arquivo, e cada participante os referencia por nome a partir dele

#### Scenario: Elemento compartilhado declara seu nível
- **WHEN** o `Modifier` de promoção do cartão de crédito é inspecionado
- **THEN** ele passa explicitamente o nível do elemento compartilhado, em vez de omitir o argumento e receber o default do Compose

#### Scenario: Participantes em módulos que não se nomeiam
- **WHEN** o `Modifier` de promoção em `:core:ui` e o chrome em `:feature:shell:impl` referenciam a escala
- **THEN** ambos a obtêm de `:core:designsystem`, e nenhum dos dois nomeia o outro

#### Scenario: Novo participante do overlay
- **WHEN** um componente passa a se declarar no overlay de transição
- **THEN** seu nível é adicionado à escala com um nome de papel, e sua posição relativa aos participantes existentes fica registrada ali

