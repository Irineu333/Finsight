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

A lista SHALL ser **ordenada**, e a ordem SHALL ser a que a tela declarou — é ela que decide o que
o usuário lê primeiro, e o que o botão executa quando há uma ação só. Uma ação SHALL declarar o
rótulo que a nomeia, o ícone que a acompanha, a identidade que a torna alcançável em teste e o que
ela executa.

A casca MUST NOT manter um mapa de rota para ações. Uma ação pode depender do estado corrente da
tela — o tipo que o formulário de categoria abre pré-selecionado depende do filtro em vigor —, e
um catálogo estático na casca afirmaria uma ação que a tela já não oferece.

As ações MUST NOT residir no `ChromeConfig`. Ele é comparado por igualdade estrutural para animar
a chrome, e uma lista de funções nunca é igual a si mesma entre recomposições — guardá-la ali
reiniciaria a transição indefinidamente. O canal das ações SHALL ser distinto do canal da
configuração, ainda que ambos partam da mesma tela.

A casca MUST NOT desenhar ações que não sejam do destino em foco. Durante uma transição de
navegação duas telas estão compostas ao mesmo tempo, e ambas publicam; a publicação SHALL portanto
carregar a identidade do destino que a originou, e a casca SHALL desenhar apenas a que corresponde
ao destino corrente. Ordem de composição MUST NOT ser usada como garantia: ela não é contratual
entre telas irmãs, e o requisito é sobre o que se desenha, não sobre quem escreve primeiro.

Cada destino SHALL ter o seu próprio registro de ações. Uma tela que se dispõe SHALL limpar apenas
o registro da sua identidade, e MUST NOT apagar o de outra — a tela que sai se dispõe depois de a
tela que entra já ter publicado.

#### Scenario: Ação que depende do estado da tela
- **WHEN** o filtro da tela de categorias está em despesas e o usuário aciona a primeira ação
- **THEN** o formulário abre com o tipo despesa pré-selecionado; trocado o filtro para receitas, a
  mesma ação passa a abrir com receita, sem que a casca tenha sido alterada

#### Scenario: Navegação entre telas com ações diferentes
- **WHEN** o usuário navega de uma tela para outra que oferece ações distintas
- **THEN** em nenhum frame o botão exibe as ações da tela anterior, e em nenhum frame da transição
  ele fica sem as ações do destino que entra

#### Scenario: Tela que sai se dispõe depois da que entra
- **WHEN** a animação de saída termina e a tela anterior se dispõe
- **THEN** as ações do destino corrente permanecem publicadas

#### Scenario: Casca inspecionada
- **WHEN** a casca é inspecionada
- **THEN** ela não nomeia nenhuma feature nem obtém entry point algum para montar as ações do botão

### Requirement: O botão tem uma forma só, e a lista decide o que acioná-lo faz

O botão SHALL ter **a mesma forma e o mesmo tamanho em toda tela**, qualquer que seja o número de
ações publicadas. A quantidade de ações MUST NOT alterar a sua silhueta: um botão que cresce ao
ganhar uma segunda ação anuncia, no espaço que ocupa, algo que o usuário não pediu para saber.

O que **acioná-lo faz** SHALL ser derivado da quantidade de ações publicadas, e MUST NOT ser
declarado pela tela:

- **Nenhuma ação** — o botão não é exibido.
- **Uma ação** — acioná-lo executa a ação, e o botão carrega a identidade dela.
- **Duas ou mais** — acioná-lo abre o menu. O botão não é então nenhuma ação em particular: ele
  MUST NOT carregar a identidade de nenhuma delas, e SHALL ter uma identidade própria.

Com duas ou mais ações, o menu SHALL listar **todas** as ações publicadas, a primeira inclusive:
o botão deixou de executá-la, e uma ação fora do menu não teria como ser alcançada. As ações
SHALL ser exibidas com rótulo visível, e MUST NOT ser oferecidas apenas por ícone.

Enquanto o menu está aberto, o conteúdo atrás dele SHALL ser obscurecido, e acionar a área
obscurecida SHALL fechá-lo sem executar ação alguma.

#### Scenario: Tela que publica uma ação
- **WHEN** a tela em foco publica uma única ação
- **THEN** um toque executa a ação, e o botão responde pela identidade que a tela declarou para ela

#### Scenario: Tela que publica três ações
- **WHEN** a tela em foco publica três ações e o usuário aciona o botão
- **THEN** o menu abre com as três, nenhuma delas executada, e o botão tem o mesmo tamanho que tem
  numa tela de ação única

#### Scenario: A primeira ação numa tela com menu
- **WHEN** a tela publica mais de uma ação e o usuário quer a primeira delas
- **THEN** ela está no menu, como as demais, e é alcançada abrindo-o

#### Scenario: Menu aberto e dispensado
- **WHEN** o menu está aberto e o usuário toca fora dele
- **THEN** o menu fecha e nenhuma ação é executada

### Requirement: O botão existe em toda tela, e a ausência de ação própria não o apaga

O app SHALL ter uma **ação universal** — registrar uma transação —, que não pertence a tela alguma
e é a razão de o app existir. Uma tela que não publica ação nenhuma SHALL ter o botão com a ação
universal, e MUST NOT ficar sem botão por não ter ação própria. Uma tela que publica ações SHALL ter
o botão com as suas.

A visibilidade do botão MUST NOT depender de o destino ser uma aba primária, nem de o seletor estar
visível. As duas regras são independentes: a bottom bar continua restrita às abas primárias, e o
botão não.

Uma tela SHALL poder **suprimir** o botão declarando-o na configuração de chrome. Suprimir é um ato
explícito, e MUST NOT ser confundido com não ter ação própria — a tela que nada declara recebe a
ação universal, e só a que pede para não ter botão fica sem ele.

#### Scenario: Tela sem ação própria em janela larga
- **WHEN** o usuário está numa tela que não publica ação alguma (relatórios, configurações, a fatura
  de um cartão) em janela larga
- **THEN** o botão é exibido com a ação universal, como já era antes desta capability existir

#### Scenario: Tela sem ação própria no celular
- **WHEN** o usuário está numa tela que não publica ação alguma e o destino não é uma aba primária
- **THEN** a bottom bar é ocultada e o botão permanece, com a ação universal

#### Scenario: Tela em modo de edição
- **WHEN** o painel entra em modo de edição e suprime o botão na configuração de chrome
- **THEN** o botão é ocultado, e a ação universal não o substitui

### Requirement: A posição do botão acompanha o seletor

A posição do botão SHALL acompanhar o seletor: com a bottom bar visível, o botão é central e
ancorado a ela; sem a bottom bar, o botão fica no canto da área de conteúdo; em janela larga, o
botão é o `header` da rail. É onde cada botão de ação do app já se encontra, e a consolidação
MUST NOT mover nenhum deles.

Sem a bottom bar, o botão SHALL respeitar os insets da janela. A casca desenha o conteúdo com os
insets zerados e cada tela os reintroduz; um botão que herdasse os insets da casca seria desenhado
sob a barra de navegação do sistema.

Em janela larga o menu SHALL abrir ao lado do botão, e nenhuma das suas ações SHALL ser recortada
pelos limites da rail. O menu MUST NOT deixar de participar do overlay de transição por causa disso,
nem deixar de publicar as suas identidades de teste na raiz de composição da janela.

#### Scenario: Tela empilhada no celular
- **WHEN** a janela é estreita e o destino não é uma aba primária
- **THEN** a bottom bar é ocultada e o botão fica no canto da área de conteúdo, acima da barra de
  navegação do sistema

#### Scenario: Aba primária no celular
- **WHEN** a janela é estreita e o destino é uma aba primária
- **THEN** a bottom bar é exibida e o botão é central, ancorado a ela

#### Scenario: Menu aberto em janela larga
- **WHEN** o botão é o `header` da rail e o usuário abre o menu
- **THEN** as ações são exibidas ao lado do botão, sobre o conteúdo, nenhuma delas recortada pela
  rail, e todas alcançáveis pela automação de teste

### Requirement: Toda ação é nomeada e alcançável

Toda ação SHALL declarar um rótulo internacionalizado, presente nos dois idiomas que o app publica.
O botão SHALL expor uma descrição acessível do que acioná-lo faz — a ação, quando ele executa uma;
o menu, quando ele o abre — e cada ação SHALL ser alcançável por uma identidade estável de teste
declarada junto com ela.

A identidade de teste de uma ação SHALL ser a que a própria tela declara, de modo que a superfície
de automação não dependa de qual componente a desenha. Onde a tela publica mais de uma ação, essa
identidade vive no item do menu, e alcançá-la passa por abrir o menu.

#### Scenario: Ação dirigida em teste numa tela de ação única
- **WHEN** um fluxo E2E aciona a criação de taxa pela identidade que a tela de taxas declara
- **THEN** o formulário é aberto, independentemente de o botão ser desenhado pela casca

#### Scenario: Ação dirigida em teste numa tela com menu
- **WHEN** um fluxo E2E quer a criação de conta, e a tela de contas publica mais de uma ação
- **THEN** ele aciona o botão pela identidade própria dele, e então a ação pela identidade que a
  tela declarou — o id da ação não muda por ela estar no menu

#### Scenario: Rótulo ausente num idioma
- **WHEN** a chave de rótulo de uma ação existe em apenas um dos arquivos de string
- **THEN** isso é um defeito: toda chave nova entra nos dois no mesmo commit
