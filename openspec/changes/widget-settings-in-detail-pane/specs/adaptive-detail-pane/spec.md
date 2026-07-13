## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: Rolagem do conteúdo adaptativo em ambas as apresentações

Uma superfície adaptativa SHALL poder rolar seu conteúdo quando ele exceder a altura disponível, tanto na apresentação em **painel** quanto na apresentação em **bottom sheet**. A superfície MUST NOT prover rolagem interna própria concorrente com a rolagem do container; o container (painel ou sheet) SHALL ser responsável pela rolagem.

#### Scenario: Conteúdo longo no painel
- **WHEN** uma superfície adaptativa com conteúdo mais alto que o painel é exibida em janela larga
- **THEN** o conteúdo pode ser rolado dentro do painel, sem erro de medição por rolagem aninhada

#### Scenario: Conteúdo longo no bottom sheet
- **WHEN** uma superfície adaptativa com conteúdo mais alto que a área visível é exibida como bottom sheet em janela estreita
- **THEN** o conteúdo pode ser rolado dentro do sheet
