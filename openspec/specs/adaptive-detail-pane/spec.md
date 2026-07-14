# adaptive-detail-pane Specification

## Purpose
TBD - created by syncing change adaptive-detail-pane. Update Purpose after archive.
## Requirements
### Requirement: Apresentação adaptativa dos detalhes por largura de janela

As superfícies **adaptativas** SHALL adaptar sua apresentação ao eixo de **largura de janela**: em janelas estreitas (abaixo do breakpoint) SHALL ser exibidas como `ModalBottomSheet`; em janelas largas (no ou acima do breakpoint) SHALL ser exibidas num **painel de detalhe** fixo à direita. São superfícies adaptativas os modais de **detalhe** (`view*`: operação, ajuste, categoria, orçamento, recorrência) e as **configurações do widget do dashboard** (modo de edição). Modais de formulário e de confirmação MUST NOT ser afetados — permanecem `ModalBottomSheet` em qualquer largura. A decisão painel-vs-sheet SHALL ser feita pela casca a partir da largura da janela; a feature que abre a superfície MUST NOT decidir a apresentação — apenas escolhe o mecanismo (`DetailPaneController`).

#### Scenario: Detalhe em janela estreita
- **WHEN** um detalhe `view*` é aberto e a largura da janela está abaixo do breakpoint
- **THEN** ele é exibido como `ModalBottomSheet`, ancorado embaixo, como hoje

#### Scenario: Detalhe em janela larga
- **WHEN** um detalhe `view*` é aberto e a largura da janela está no ou acima do breakpoint
- **THEN** ele é exibido no painel de detalhe fixo à direita, e nenhum bottom sheet é ancorado embaixo para esse detalhe

#### Scenario: Configurações do widget em janela larga
- **WHEN** no modo de edição do dashboard o usuário toca num widget e a largura da janela está no ou acima do breakpoint
- **THEN** as configurações do widget são exibidas no painel de detalhe à direita, com o dashboard editável permanecendo visível à esquerda, sem bottom sheet ancorado embaixo

#### Scenario: Configurações do widget em janela estreita
- **WHEN** no modo de edição do dashboard o usuário toca num widget e a largura da janela está abaixo do breakpoint
- **THEN** as configurações do widget são exibidas como `ModalBottomSheet`, ancoradas embaixo, como hoje

#### Scenario: Formulários e confirmações não são afetados
- **WHEN** um modal de formulário ou confirmação é aberto em qualquer largura de janela
- **THEN** ele é exibido como `ModalBottomSheet`, sem usar o painel de detalhe

### Requirement: Painel de detalhe fixo e reservado em janelas largas

Em janelas largas, o painel de detalhe SHALL ser **fixo à direita** e **sempre reservado** — o espaço do painel é mantido mesmo quando nenhum detalhe está selecionado. Quando nenhum detalhe está aberto, o painel SHALL exibir um **empty-state**. Quando um detalhe está aberto, o painel SHALL prover um controle de **fechar (X)** que dispensa o detalhe atual e retorna ao empty-state. O painel SHALL coexistir com o conteúdo e com o rail, formando o arranjo `rail | conteúdo | painel`.

#### Scenario: Empty-state sem seleção
- **WHEN** a janela é larga e nenhum detalhe está aberto
- **THEN** o painel permanece reservado à direita exibindo o empty-state, e o conteúdo ocupa o espaço restante à esquerda do painel

#### Scenario: Fechar o detalhe
- **WHEN** um detalhe está aberto no painel e o usuário aciona o controle de fechar (X)
- **THEN** o detalhe é dispensado e o painel volta ao empty-state, sem fechar o app nem navegar

#### Scenario: Arranjo de três colunas
- **WHEN** a janela é larga e o rail está visível
- **THEN** o layout apresenta rail à esquerda, conteúdo no centro e o painel de detalhe reservado à direita

### Requirement: Slot único — detalhe substitui detalhe

O painel de detalhe SHALL manter no máximo **um** detalhe por vez (slot único). Abrir um novo detalhe enquanto outro está aberto SHALL **substituir** o conteúdo do painel pelo novo detalhe. O painel MUST NOT manter histórico de navegação "voltar" interno entre detalhes.

#### Scenario: Abrir detalhe a partir de outro detalhe
- **WHEN** um detalhe está aberto no painel e o usuário abre outro detalhe a partir dele (ex.: da operação para a recorrência vinculada)
- **THEN** o painel passa a exibir o novo detalhe, substituindo o anterior, sem oferecer um "voltar" para o detalhe anterior

### Requirement: Preservação de estado ao cruzar o breakpoint

Um detalhe aberto SHALL preservar seu estado ao cruzar o breakpoint de largura: redimensionar a janela de estreita para larga (ou vice-versa) SHALL transformar a apresentação entre sheet e painel **sem reabrir** o detalhe e **sem perder** o estado do seu ViewModel/entrada.

#### Scenario: Estreito para largo com detalhe aberto
- **WHEN** um detalhe está aberto como bottom sheet e a janela é redimensionada para larga
- **THEN** o mesmo detalhe passa a ser exibido no painel à direita, preservando seu estado, sem reabrir

#### Scenario: Largo para estreito com detalhe aberto
- **WHEN** um detalhe está aberto no painel e a janela é redimensionada para estreita
- **THEN** o mesmo detalhe passa a ser exibido como bottom sheet, preservando seu estado, sem reabrir

### Requirement: Mecanismo de detalhe distinto do gerenciador de modais transitórios

O app SHALL prover um `DetailPaneController` dedicado para as superfícies adaptativas, **distinto** do `ModalManager` de modais transitórios/empilháveis. A **chamada** que abre a superfície de UI SHALL escolher o mecanismo — superfícies adaptativas (detalhes `view*` e configurações do widget do dashboard) via `DetailPaneController`, formulários/confirmações via `ModalManager`. O `ModalManager` SHALL permanecer inalterado no seu papel de pilha de overlay. Uma superfície adaptativa aberta no painel SHALL coexistir com modais transitórios abertos por cima dela (ex.: abrir um formulário a partir de um detalhe), com os modais transitórios renderizados na camada de overlay acima do painel.

#### Scenario: Detalhe via DetailPaneController
- **WHEN** uma feature abre um detalhe `view*`
- **THEN** ela usa `DetailPaneController`, e a apresentação (painel ou sheet) é resolvida pela largura da janela

#### Scenario: Configurações do widget via DetailPaneController
- **WHEN** o dashboard abre as configurações de um widget no modo de edição
- **THEN** ele usa `DetailPaneController`, e a apresentação (painel ou sheet) é resolvida pela largura da janela

#### Scenario: Formulário empilhado sobre um detalhe
- **WHEN** em janela larga, um detalhe está no painel e o usuário abre um formulário a partir dele (ex.: editar)
- **THEN** o formulário é exibido como modal de overlay via `ModalManager`, por cima do painel, e o detalhe permanece visível no painel

### Requirement: Detalhes dirigidos por id com carregamento reativo

As superfícies de detalhe `view*` (operação, ajuste, categoria, orçamento, recorrência) SHALL receber **apenas o identificador** da entidade e, quando aplicável, a **configuração de apresentação não-recuperável** (ex.: a perspectiva que seleciona qual transação exibir). Elas MUST NOT receber o objeto de domínio já carregado. Cada detalhe SHALL observar a entidade **por id** e expor um estado de UI com exatamente três apresentações: **carregando**, **erro** e **conteúdo**. O estado inicial SHALL ser **carregando**. Enquanto o detalhe estiver aberto, cada mudança na entidade observada SHALL re-emitir **conteúdo** atualizado, re-renderizando o detalhe **in-place** sem fechá-lo. Este comportamento SHALL valer tanto na apresentação em painel (janela larga) quanto em bottom sheet (janela estreita). A apresentação de **erro** SHALL ocorrer **exclusivamente** quando a **primeira** emissão da observação for vazia (id inexistente / falha de obtenção); uma vez exibido **conteúdo**, uma emissão vazia posterior NÃO leva a **erro** (ver auto-dispensa reativa). A falha de obtenção na primeira emissão SHALL ser **registrada para observabilidade** (crash reporting), para que o erro não seja silencioso.

#### Scenario: Carregando é o estado inicial
- **WHEN** um detalhe `view*` é aberto por id
- **THEN** ele exibe a apresentação de **carregando** até a primeira emissão da entidade observada

#### Scenario: Conteúdo ao obter a entidade
- **WHEN** a observação por id emite a entidade
- **THEN** o detalhe passa a exibir a apresentação de **conteúdo** com os dados da entidade

#### Scenario: Erro ao não obter a entidade
- **WHEN** a primeira emissão da observação por id é vazia (id inexistente ou falha de obtenção), sem que o detalhe tenha exibido conteúdo antes
- **THEN** o detalhe exibe a apresentação de **erro**, sem ficar preso em carregando

#### Scenario: Falha de carregamento é registrada para observabilidade
- **WHEN** a primeira emissão da observação por id é vazia (id inexistente ou falha de obtenção)
- **THEN** a falha é registrada no serviço de crash reporting, de modo que o erro não permaneça silencioso

#### Scenario: Editar re-renderiza o detalhe in-place
- **WHEN** o usuário edita a entidade a partir de um formulário aberto sobre o detalhe e salva a edição
- **THEN** o detalhe permanece aberto e re-renderiza com os dados atualizados, sem ser dispensado

### Requirement: Auto-dispensa reativa do detalhe ao desaparecer a entidade

Um detalhe `view*` que já exibiu **conteúdo** SHALL se **auto-dispensar** quando a entidade observada desaparecer (emissão vazia após ter havido conteúdo), voltando o painel ao empty-state em janela larga ou fechando o bottom sheet em janela estreita. A auto-dispensa SHALL ser dirigida pela própria observação do detalhe e MUST NOT depender do teardown de modais transitórios (`dismissAll()`). Uma emissão vazia **antes** de qualquer conteúdo MUST NOT ser tratada como desaparecimento — SHALL levar à apresentação de **erro** (ver requisito de carregamento reativo).

#### Scenario: Excluir a entidade de dentro do detalhe fecha o detalhe
- **WHEN** o usuário exclui a entidade a partir do seu detalhe `view*` e a exclusão é efetivada
- **THEN** a observação por id emite vazio após ter havido conteúdo, e o detalhe se auto-dispensa, retornando ao empty-state em janela larga

#### Scenario: Excluir a entidade por outro caminho fecha o detalhe aberto
- **WHEN** um detalhe `view*` está aberto e a entidade observada é excluída por qualquer fluxo enquanto o detalhe permanece na tela
- **THEN** o detalhe se auto-dispensa reativamente, sem exibir dados fantasma

### Requirement: Rolagem do conteúdo adaptativo em ambas as apresentações

Uma superfície adaptativa SHALL distinguir seu **corpo** (conteúdo) das suas **ações** (rodapé de botões). O corpo SHALL poder rolar quando exceder a altura disponível, tanto na apresentação em **painel** quanto na apresentação em **bottom sheet**. A superfície MUST NOT prover rolagem interna própria concorrente com a rolagem do container; a rolagem do corpo SHALL ser responsabilidade do container (painel ou sheet).

Na apresentação em **painel** (janela larga), as ações SHALL ser **fixadas no rodapé** do painel, fora da área rolável — apenas o corpo rola. Quando houver conteúdo do corpo rolável por baixo do rodapé fixo, uma **elevação/sombra sutil** SHALL separar visualmente as ações do corpo; sem conteúdo rolável, a separação SHALL desaparecer.

Na apresentação em **bottom sheet** (janela estreita), o corpo e as ações SHALL rolar juntos no mesmo container, como hoje; a elevação de separação das ações MUST NOT se aplicar nessa apresentação.

#### Scenario: Conteúdo longo no painel
- **WHEN** uma superfície adaptativa com corpo mais alto que o painel é exibida em janela larga
- **THEN** o corpo pode ser rolado dentro do painel, sem erro de medição por rolagem aninhada, e as ações permanecem fixas no rodapé sem rolar

#### Scenario: Separação visual das ações fixas
- **WHEN** o corpo de uma superfície adaptativa em painel é mais alto que o espaço disponível e há conteúdo rolável por baixo do rodapé
- **THEN** uma elevação/sombra sutil separa as ações fixas do corpo

#### Scenario: Conteúdo curto no painel
- **WHEN** uma superfície adaptativa com corpo mais baixo que o painel é exibida em janela larga
- **THEN** as ações permanecem ancoradas no rodapé do painel (não flutuam junto ao corpo) e não há elevação de separação, pois não há conteúdo rolável

#### Scenario: Conteúdo longo no bottom sheet
- **WHEN** uma superfície adaptativa com corpo mais alto que a área visível é exibida como bottom sheet em janela estreita
- **THEN** o corpo e as ações rolam juntos dentro do sheet, como hoje, sem rodapé fixo

