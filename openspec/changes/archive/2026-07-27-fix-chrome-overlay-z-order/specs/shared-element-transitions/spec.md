## MODIFIED Requirements

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

## ADDED Requirements

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
