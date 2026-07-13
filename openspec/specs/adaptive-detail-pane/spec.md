# adaptive-detail-pane Specification

## Purpose
TBD - created by syncing change adaptive-detail-pane. Update Purpose after archive.

## Requirements
### Requirement: Apresentação adaptativa dos detalhes por largura de janela

Os modais de **detalhe** (`view*`: operação, ajuste, categoria, orçamento, recorrência) SHALL adaptar sua apresentação ao eixo de **largura de janela**: em janelas estreitas (abaixo do breakpoint) SHALL ser exibidos como `ModalBottomSheet` (comportamento atual); em janelas largas (no ou acima do breakpoint) SHALL ser exibidos num **painel de detalhe** fixo à direita. Modais de formulário e de confirmação (não-`view*`) MUST NOT ser afetados — permanecem `ModalBottomSheet` em qualquer largura. A decisão painel-vs-sheet SHALL ser feita pela casca a partir da largura da janela; a feature que abre o detalhe MUST NOT decidir a superfície.

#### Scenario: Detalhe em janela estreita
- **WHEN** um detalhe `view*` é aberto e a largura da janela está abaixo do breakpoint
- **THEN** ele é exibido como `ModalBottomSheet`, ancorado embaixo, como hoje

#### Scenario: Detalhe em janela larga
- **WHEN** um detalhe `view*` é aberto e a largura da janela está no ou acima do breakpoint
- **THEN** ele é exibido no painel de detalhe fixo à direita, e nenhum bottom sheet é ancorado embaixo para esse detalhe

#### Scenario: Formulários e confirmações não são afetados
- **WHEN** um modal de formulário ou confirmação (não-`view*`) é aberto em qualquer largura de janela
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

O app SHALL prover um `DetailPaneController` dedicado para os detalhes adaptativos, **distinto** do `ModalManager` de modais transitórios/empilháveis. A **chamada** que abre a superfície de UI SHALL escolher o mecanismo — detalhes adaptativos via `DetailPaneController`, formulários/confirmações via `ModalManager`. O `ModalManager` SHALL permanecer inalterado no seu papel de pilha de overlay. Um detalhe adaptativo aberto no painel SHALL coexistir com modais transitórios abertos por cima dele (ex.: abrir um formulário a partir de um detalhe), com os modais transitórios renderizados na camada de overlay acima do painel.

#### Scenario: Detalhe via DetailPaneController
- **WHEN** uma feature abre um detalhe `view*`
- **THEN** ela usa `DetailPaneController`, e a apresentação (painel ou sheet) é resolvida pela largura da janela

#### Scenario: Formulário empilhado sobre um detalhe
- **WHEN** em janela larga, um detalhe está no painel e o usuário abre um formulário a partir dele (ex.: editar)
- **THEN** o formulário é exibido como modal de overlay via `ModalManager`, por cima do painel, e o detalhe permanece visível no painel
