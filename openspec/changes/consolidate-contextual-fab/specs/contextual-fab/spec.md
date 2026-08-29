## ADDED Requirements

### Requirement: O app tem um único botão de ação flutuante

O app SHALL ter exatamente **um** botão de ação flutuante, desenhado pela casca. Nenhuma tela
SHALL declarar `floatingActionButton` no seu próprio `Scaffold`. Dois botões de ação na mesma
janela MUST NOT ocorrer em nenhuma largura.

A regra é da forma, não da disciplina: a tela que quisesse um segundo botão não teria onde
declará-lo. O botão é da casca, e o que a tela oferece a ele são ações.

#### Scenario: Janela larga com uma seção que tem ação própria
- **WHEN** a janela é larga e o usuário está numa seção que oferece ação de criação (ex.: Contas)
- **THEN** existe um único botão de ação na janela, no `header` da rail, e a área de conteúdo não
  desenha botão algum

#### Scenario: Tela inspecionada
- **WHEN** o `Scaffold` de qualquer tela de feature é inspecionado
- **THEN** ele não declara `floatingActionButton`

### Requirement: A tela declara as suas ações; a casca decide a forma

Cada tela SHALL declarar as ações que o botão oferece **enquanto ela está em foco**, publicando-as
pelo canal de chrome (`ChromeEffect`). A casca SHALL renderizá-las sem conhecer a feature que as
originou: ela decide *como* elas aparecem, e nunca *quais* são.

A lista SHALL ser **ordenada**, e a primeira posição SHALL ser a ação primária. Uma ação SHALL
declarar o rótulo que a nomeia, o ícone que a acompanha, a identidade que a torna alcançável em
teste e o que ela executa.

A casca MUST NOT manter um mapa de rota para ações. Uma ação pode depender do estado corrente da
tela — o tipo que o formulário de categoria abre pré-selecionado depende do filtro em vigor —, e
um catálogo estático na casca afirmaria uma ação que a tela já não oferece.

As ações MUST NOT residir no `ChromeConfig`. Ele é comparado por igualdade estrutural para animar
a chrome, e uma lista de funções nunca é igual a si mesma entre recomposições — guardá-la ali
reiniciaria a transição indefinidamente. O canal das ações SHALL ser distinto do canal da
configuração, ainda que ambos partam da mesma tela.

Publicar as ações SHALL acontecer **durante** a composição da tela, e não depois dela. Uma
publicação diferida exibe, no primeiro frame após navegar, as ações da tela anterior.

#### Scenario: Ação que depende do estado da tela
- **WHEN** o filtro da tela de categorias está em despesas e o usuário aciona a ação primária
- **THEN** o formulário abre com o tipo despesa pré-selecionado; trocado o filtro para receitas, a
  mesma ação primária passa a abrir com receita, sem que a casca tenha sido alterada

#### Scenario: Navegação entre telas com ações diferentes
- **WHEN** o usuário navega de uma tela para outra que oferece ações distintas
- **THEN** em nenhum frame o botão exibe as ações da tela anterior

#### Scenario: Casca inspecionada
- **WHEN** a casca é inspecionada
- **THEN** ela não nomeia nenhuma feature nem obtém entry point algum para montar as ações do botão

### Requirement: A ação primária custa um toque, e a forma do botão diz quantas há

A forma do botão SHALL ser **derivada** da quantidade de ações publicadas, e MUST NOT ser
declarada pela tela:

- **Nenhuma ação** — o botão não é exibido.
- **Uma ação** — botão simples. Acioná-lo executa a ação.
- **Duas ou mais** — botão com duas áreas: o corpo, que executa a ação primária, e um controle de
  expansão, que revela as demais. O corpo MUST NOT abrir o menu.

Acionar a ação primária SHALL custar **um toque** em toda tela, qualquer que seja o número de
ações publicadas. As ações secundárias SHALL ser exibidas com rótulo visível, e MUST NOT ser
oferecidas apenas por ícone.

Enquanto o menu está aberto, o conteúdo atrás dele SHALL ser obscurecido, e acionar a área
obscurecida SHALL fechá-lo sem executar ação alguma.

#### Scenario: Tela que publica uma ação
- **WHEN** a tela em foco publica uma única ação
- **THEN** o botão não exibe controle de expansão, e um toque executa a ação

#### Scenario: Tela que publica três ações
- **WHEN** a tela em foco publica três ações e o usuário toca o corpo do botão
- **THEN** a ação primária é executada e o menu não é aberto

#### Scenario: Menu aberto e dispensado
- **WHEN** o menu está aberto e o usuário toca fora dele
- **THEN** o menu fecha e nenhuma ação é executada

### Requirement: O botão aparece porque a tela tem ação, e não porque o seletor apareceu

A visibilidade do botão SHALL ser derivada exclusivamente de haver ação publicada pela tela em
foco. Ela MUST NOT depender de o destino ser uma aba primária, nem de o seletor estar visível.

O `ChromeConfig` MUST NOT declarar a visibilidade do botão. Uma tela que não quer o botão publica
zero ações; uma que o quer publica as suas. A configuração de chrome rege o seletor, e nada mais.

A **posição** do botão SHALL acompanhar o seletor: com a bottom bar visível, o botão é central e
ancorado a ela; sem a bottom bar, o botão fica no canto da área de conteúdo; em janela larga, o
botão é o `header` da rail. Em janela larga o menu SHALL abrir ao lado do botão, e não acima dele.

#### Scenario: Tela empilhada no celular
- **WHEN** a janela é estreita, o destino não é uma aba primária e a tela publica ações
- **THEN** a bottom bar é ocultada e o botão permanece visível, no canto da área de conteúdo

#### Scenario: Aba primária no celular
- **WHEN** a janela é estreita, o destino é uma aba primária e a tela publica ações
- **THEN** a bottom bar é exibida e o botão é central, ancorado a ela

#### Scenario: Tela em modo de edição
- **WHEN** o painel entra em modo de edição e publica zero ações
- **THEN** o botão é ocultado, sem que nenhuma configuração de visibilidade tenha sido declarada

#### Scenario: Menu aberto em janela larga
- **WHEN** o botão é o `header` da rail e o usuário abre o menu
- **THEN** as ações são exibidas ao lado do botão, sobre o conteúdo, e nenhuma delas é recortada
  pelos limites da rail

### Requirement: Toda ação é nomeada e alcançável

Toda ação SHALL declarar um rótulo internacionalizado, presente nos dois idiomas que o app publica.
O botão SHALL expor uma descrição acessível correspondente à sua ação primária, e cada ação do menu
SHALL ser alcançável por uma identidade estável de teste declarada junto com a ação.

A identidade de teste da ação primária de uma tela SHALL ser a que a própria tela declara, de modo
que a superfície de automação não dependa de qual componente a desenha.

#### Scenario: Ação primária dirigida em teste
- **WHEN** um fluxo E2E aciona a ação de criar conta pela identidade que a tela de contas declara
- **THEN** o formulário de conta é aberto, independentemente de o botão ser desenhado pela casca

#### Scenario: Rótulo ausente num idioma
- **WHEN** a chave de rótulo de uma ação existe em apenas um dos arquivos de string
- **THEN** isso é um defeito: toda chave nova entra nos dois no mesmo commit
