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

### Requirement: Teardown de overlays inclui o painel de detalhe

O teardown de todos os overlays transitórios (`ModalManager.dismissAll()`) SHALL também dispensar o detalhe atual do `DetailPaneController`. Um detalhe `view*` aberto (painel em janela larga ou bottom sheet em janela estreita) MUST NOT sobreviver a um `dismissAll()`. Quando nenhum detalhe está aberto, o teardown SHALL ser um no-op sobre o painel. Os fluxos que disparam o teardown (exclusões e submissões de formulário) MUST NOT precisar referenciar o `DetailPaneController` diretamente.

#### Scenario: Excluir a entidade de dentro do detalhe fecha o detalhe
- **WHEN** o usuário exclui uma entidade a partir do seu detalhe `view*` e a confirmação chama `dismissAll()`
- **THEN** tanto a confirmação quanto o detalhe (painel ou sheet) são dispensados, e o painel retorna ao empty-state em janela larga

#### Scenario: Teardown sem detalhe aberto
- **WHEN** `dismissAll()` é chamado e nenhum detalhe está aberto no `DetailPaneController`
- **THEN** apenas os modais transitórios são dispensados, sem efeito sobre o painel

#### Scenario: Submeter formulário a partir do detalhe fecha o detalhe
- **WHEN** o usuário salva uma edição aberta a partir de um detalhe `view*` e o form ViewModel chama `dismissAll()`
- **THEN** o formulário e o detalhe são dispensados juntos, evitando exibição de dados obsoletos no detalhe

### Requirement: Rolagem do conteúdo adaptativo em ambas as apresentações

Uma superfície adaptativa SHALL poder rolar seu conteúdo quando ele exceder a altura disponível, tanto na apresentação em **painel** quanto na apresentação em **bottom sheet**. A superfície MUST NOT prover rolagem interna própria concorrente com a rolagem do container; o container (painel ou sheet) SHALL ser responsável pela rolagem.

#### Scenario: Conteúdo longo no painel
- **WHEN** uma superfície adaptativa com conteúdo mais alto que o painel é exibida em janela larga
- **THEN** o conteúdo pode ser rolado dentro do painel, sem erro de medição por rolagem aninhada

#### Scenario: Conteúdo longo no bottom sheet
- **WHEN** uma superfície adaptativa com conteúdo mais alto que a área visível é exibida como bottom sheet em janela estreita
- **THEN** o conteúdo pode ser rolado dentro do sheet
