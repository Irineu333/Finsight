## MODIFIED Requirements

### Requirement: Apresentação adaptativa dos detalhes por largura de janela

As superfícies **adaptativas** SHALL adaptar sua apresentação ao eixo de **largura de janela**: em janelas estreitas (abaixo do breakpoint) SHALL ser exibidas como `ModalBottomSheet`; em janelas largas (no ou acima do breakpoint) SHALL ser exibidas num **painel de detalhe** fixo à direita. São superfícies adaptativas os modais de **detalhe** (`view*`: operação, ajuste, categoria, orçamento, recorrência). Modais de formulário e de confirmação MUST NOT ser afetados — permanecem `ModalBottomSheet` em qualquer largura. As **configurações do widget do dashboard** (modo de edição) MUST NOT ser uma superfície adaptativa: são um modal transitório, exibido por cima do editor em qualquer largura, porque em janela larga o painel de detalhe é ocupado pela vitrine de widgets do próprio editor. A decisão painel-vs-sheet SHALL ser feita pela casca a partir da largura da janela; a feature que abre a superfície MUST NOT decidir a apresentação — apenas escolhe o mecanismo (`DetailPaneController`).

#### Scenario: Detalhe em janela estreita
- **WHEN** um detalhe `view*` é aberto e a largura da janela está abaixo do breakpoint
- **THEN** ele é exibido como `ModalBottomSheet`, ancorado embaixo, como hoje

#### Scenario: Detalhe em janela larga
- **WHEN** um detalhe `view*` é aberto e a largura da janela está no ou acima do breakpoint
- **THEN** ele é exibido no painel de detalhe fixo à direita, e nenhum bottom sheet é ancorado embaixo para esse detalhe

#### Scenario: Configurações do widget em qualquer largura
- **WHEN** no modo de edição do dashboard o usuário toca num widget, em qualquer largura de janela
- **THEN** as configurações do widget são exibidas como modal transitório por cima do editor, e o painel de detalhe — quando existe — continua exibindo a vitrine de widgets

#### Scenario: Formulários e confirmações não são afetados
- **WHEN** um modal de formulário ou confirmação é aberto em qualquer largura de janela
- **THEN** ele é exibido como `ModalBottomSheet`, sem usar o painel de detalhe

### Requirement: Mecanismo de detalhe distinto do gerenciador de modais transitórios

O app SHALL prover um `DetailPaneController` dedicado para as superfícies adaptativas, **distinto** do `ModalManager` de modais transitórios/empilháveis. A **chamada** que abre a superfície de UI SHALL escolher o mecanismo — superfícies adaptativas (detalhes `view*`) e detalhes pane-only (a vitrine de widgets do editor do dashboard) via `DetailPaneController`, formulários/confirmações e as configurações do widget do dashboard via `ModalManager`. O `ModalManager` SHALL permanecer inalterado no seu papel de pilha de overlay. Uma superfície aberta no painel SHALL coexistir com modais transitórios abertos por cima dela (ex.: abrir um formulário a partir de um detalhe, ou as configurações de um widget com a vitrine no painel), com os modais transitórios renderizados na camada de overlay acima do painel.

#### Scenario: Detalhe via DetailPaneController
- **WHEN** uma feature abre um detalhe `view*`
- **THEN** ela usa `DetailPaneController`, e a apresentação (painel ou sheet) é resolvida pela largura da janela

#### Scenario: Configurações do widget via ModalManager
- **WHEN** o dashboard abre as configurações de um widget no modo de edição
- **THEN** ele usa `ModalManager`, e a modal é exibida por cima do editor em qualquer largura de janela

#### Scenario: Formulário empilhado sobre um detalhe
- **WHEN** em janela larga, um detalhe está no painel e o usuário abre um formulário a partir dele (ex.: editar)
- **THEN** o formulário é exibido como modal de overlay via `ModalManager`, por cima do painel, e o detalhe permanece visível no painel

#### Scenario: Configurações empilhadas sobre a vitrine
- **WHEN** em janela extra-larga, a vitrine de widgets está no painel e o usuário toca num item da lista do editor
- **THEN** as configurações abrem como modal de overlay via `ModalManager`, por cima do painel, e a vitrine permanece visível no painel

### Requirement: Detalhes pane-only distintos de detalhes sheet-capable

O mecanismo do painel SHALL distinguir dois tipos de detalhe adaptativo: **sheet-capable** e **pane-only**. Os detalhes `view*` SHALL ser **sheet-capable** — em janela larga são exibidos no painel e, abaixo do breakpoint, rebaixados a `ModalBottomSheet` (comportamento inalterado). Um detalhe **pane-only** SHALL ser exibido **exclusivamente** no painel de detalhe (janela extra-larga) e MUST NOT ser rebaixado a `ModalBottomSheet`. A **vitrine de widgets** do editor do dashboard SHALL ser um detalhe pane-only: abaixo do breakpoint extra-largo a própria tela do editor a apresenta como sheet inline, na sua árvore de composição, e não o host do painel. Quando a janela deixa de ser extra-larga, um detalhe pane-only ativo SHALL ser **dispensado** pelo host, retornando o painel ao empty-state, em vez de transformado em bottom sheet. A distinção SHALL ser uma propriedade do próprio detalhe, para que o host escolha a apresentação sem conhecer a feature.

#### Scenario: Detalhe sheet-capable em janela estreita
- **WHEN** um detalhe sheet-capable (`view*`) está ativo e a janela está abaixo do breakpoint
- **THEN** ele é exibido como `ModalBottomSheet`, como hoje

#### Scenario: Detalhe pane-only em janela extra-larga
- **WHEN** um detalhe pane-only é aberto e a janela é extra-larga
- **THEN** ele é exibido no painel de detalhe à direita

#### Scenario: Detalhe pane-only ao sair de extra-larga
- **WHEN** um detalhe pane-only está ativo no painel e a janela é redimensionada para uma largura não extra-larga
- **THEN** o detalhe é dispensado e o painel volta ao empty-state, sem ser rebaixado a `ModalBottomSheet`

#### Scenario: Vitrine de widgets é pane-only
- **WHEN** o editor do dashboard está aberto e a janela deixa de ser extra-larga
- **THEN** o host dispensa a vitrine do painel, e a tela do editor passa a apresentá-la como sheet inline, sem sair do modo de edição
