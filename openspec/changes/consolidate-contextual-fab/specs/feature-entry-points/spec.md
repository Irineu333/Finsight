## MODIFIED Requirements

### Requirement: Tipos de acesso cross-feature à UI
O acesso a recursos de UI de outra feature SHALL ocorrer exclusivamente por: (1) navegação por rota declarada na `api` de destino; (2) modal obtido via entry point, exibido via `ModalManager` (modais transitórios: formulários e confirmações) ou via `DetailPaneController` (modais de **detalhe** adaptativos, `view*`), sendo a superfície painel-vs-sheet resolvida pela largura da janela na casca; (3) conteúdo `@Composable` retornado por entry point (caso excepcional, apenas mediante necessidade real); (4) registro do subgrafo de navegação da feature de destino via um `register()` no seu entry point que receba o `NavGraphBuilder` como context parameter, quando uma feature hospeda os destinos de outra. Import direto de composable, modal, ViewModel ou função de grafo (`NavGraphBuilder.<nome>Graph()`) de outro `impl` MUST NOT ocorrer. O mecanismo (4) permanece disponível, porém Dashboard e Transactions deixam de ser hospedados: seus `dashboardGraph()`/`transactionsGraph()` são grafos de primeiro nível invocados diretamente pelo `AppNavHost`, como as demais features.

Uma ação publicada no botão de ação da casca SHALL seguir as mesmas regras: quem publica é a **tela em foco**, e o modal que a ação abre é obtido pelo entry point da feature dona quando ele não pertence ao `impl` da tela. A casca MUST NOT obter entry point algum para montar o botão — ela desenha o que a tela publicou, e não conhece a feature que originou a ação.

Um entry point SHALL poder receber, como parâmetro, o contexto que a tela chamadora já tem à mão — a conta ou o cartão em foco, identificados pelo seu id —, de modo que o formulário nasça preenchido com o que o usuário está olhando. Esse parâmetro MUST NOT introduzir tipo declarado em `impl`, e MUST NOT tornar obrigatório um contexto que nem todo chamador possui: um chamador sem contexto SHALL poder abrir o mesmo formulário sem ele.

#### Scenario: Abertura de tela de outra feature
- **WHEN** uma feature precisa levar o usuário a uma tela de outra feature
- **THEN** ela navega pela rota da `api` de destino; o composable da tela permanece interno ao `impl` dono

#### Scenario: Dashboard e Transactions como grafos de primeiro nível
- **WHEN** o `AppNavHost` monta a navegação de Dashboard e Transactions
- **THEN** ele invoca `dashboardGraph()` e `transactionsGraph()` diretamente, sem um subgrafo agregador e sem que outra feature os hospede via `register()`

#### Scenario: Feature hospeda os destinos de outra
- **WHEN** uma feature genuinamente hospeda os destinos de outra em seu próprio subgrafo
- **THEN** ela invoca o `register()` do entry point da feature hospedada, obtido do Koin, com o `NavGraphBuilder` como context parameter, e a extensão `NavGraphBuilder.<nome>Graph()` da feature hospedada permanece interna ao seu `impl`

#### Scenario: Ação de botão servida por outra feature
- **WHEN** a tela de contas publica no botão de ação a criação de uma transação, cujo modal pertence a transactions
- **THEN** `accounts:impl` obtém o `Modal` por `TransactionsEntry.addTransactionModal(...)` e o publica como ação, sem instanciar `AddTransactionModal` de `transactions:impl` e sem que a casca conheça `TransactionsEntry`

#### Scenario: Formulário aberto com o contexto em foco
- **WHEN** a tela de cartões publica a criação de transação enquanto um cartão está em foco
- **THEN** o entry point recebe o id desse cartão e o formulário abre com ele pré-selecionado; aberto de onde não há cartão em foco, o mesmo entry point é chamado sem o parâmetro e o formulário abre sem pré-seleção

#### Scenario: Detalhe adaptativo obtido via entry point
- **WHEN** uma feature precisa exibir o detalhe `view*` de outra feature (ex.: o dashboard exibindo o detalhe de uma categoria)
- **THEN** ela injeta o `<Name>Entry` via Koin, obtém o detalhe (um `AdaptiveModal`, tipo de `:core:designsystem`) pela factory `viewXModal()` e o abre via `DetailPaneController`, sem importar nada do `impl` de destino; a superfície painel-vs-sheet é resolvida pela largura da janela na casca
