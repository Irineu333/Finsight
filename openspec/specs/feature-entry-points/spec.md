# feature-entry-points Specification

## Purpose
TBD - created by archiving change modularize-features-api-impl. Update Purpose after archive.
## Requirements
### Requirement: Entry point único por feature
Cada feature que expõe UI a outras features SHALL declarar na sua `api` uma interface única `<Nome>Entry` agrupando essa superfície pública. O `impl` SHALL implementá-la e registrá-la no módulo Koin da feature. As assinaturas do entry point SHALL referenciar apenas tipos de `:core:*` (modelos de `:core:model`, `Modal` de `:core:designsystem`) e tipos de bibliotecas já admitidas na `api` da feature (`NavGraphBuilder`, de `androidx.navigation`). Uma assinatura de entry point MUST NOT referenciar tipo declarado em qualquer `impl`.

#### Scenario: Consumo de modal de outra feature
- **WHEN** `dashboard:impl` precisa exibir o modal de pagamento de fatura de creditcards
- **THEN** ele injeta `CreditCardsEntry` via Koin, obtém o `Modal` pelo método do entry point e o exibe via `ModalManager`, sem importar nada de `creditcards:impl`

#### Scenario: Feature sem UI pública
- **WHEN** nenhuma outra feature consome UI da feature
- **THEN** a feature não declara entry point (a interface não é criada preventivamente)

#### Scenario: Entry point de feature hospedada
- **WHEN** uma feature precisa expor seu subgrafo de navegação para ser montado por outra feature
- **THEN** o método `register()` é adicionado ao seu `<Nome>Entry` existente, recebendo o `NavGraphBuilder` como context parameter, e não a uma segunda interface

### Requirement: Tipos de acesso cross-feature à UI
O acesso a recursos de UI de outra feature SHALL ocorrer exclusivamente por: (1) navegação por rota declarada na `api` de destino; (2) modal obtido via entry point, exibido via `ModalManager` (modais transitórios: formulários e confirmações) ou via `DetailPaneController` (modais de **detalhe** adaptativos, `view*`), sendo a superfície painel-vs-sheet resolvida pela largura da janela na casca; (3) conteúdo `@Composable` retornado por entry point (caso excepcional, apenas mediante necessidade real); (4) registro do subgrafo de navegação da feature de destino via um `register()` no seu entry point que receba o `NavGraphBuilder` como context parameter, quando uma feature hospeda os destinos de outra. Import direto de composable, modal, ViewModel ou função de grafo (`NavGraphBuilder.<nome>Graph()`) de outro `impl` MUST NOT ocorrer. O mecanismo (4) permanece disponível, porém Dashboard e Transactions deixam de ser hospedados: seus `dashboardGraph()`/`transactionsGraph()` são grafos de primeiro nível invocados diretamente pelo `AppNavHost`, como as demais features.

Uma ação publicada no botão de ação SHALL seguir as mesmas regras: quem publica é a **tela em foco**, e o modal que a ação abre é obtido pelo entry point da feature dona quando ele não pertence ao `impl` da tela. A casca continua obtendo por entry point a **ação universal** que serve as telas sem ação própria, e MUST NOT conhecer as ações que as telas publicam.

Um entry point SHALL poder receber, como parâmetro, o contexto que a tela chamadora já tem à mão — a conta ou o cartão em foco, identificados pelo seu id —, de modo que o formulário nasça preenchido com o que o usuário está olhando. Esse parâmetro MUST NOT introduzir tipo declarado em `impl`, e MUST NOT tornar obrigatório um contexto que nem todo chamador possui: um chamador sem contexto SHALL poder abrir o mesmo formulário sem ele.

O contexto passado SHALL ser o que a tela tem **em foco agora**, e MUST NOT ser lido do argumento de rota que a abriu. A rota carrega a seleção inicial; o foco corrente muda enquanto a tela vive, e um formulário preenchido a partir da rota afirmaria o item que o usuário abriu, e não o que ele está olhando.

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
- **THEN** `accounts:impl` obtém o `Modal` por `TransactionsEntry.addTransactionModal(...)` e o publica como ação, sem instanciar `AddTransactionModal` de `transactions:impl`; que a casca também obtenha esse entry point para a ação universal é irrelevante para a tela, que não passa por ela

#### Scenario: Formulário aberto com o contexto em foco
- **WHEN** a tela de cartões publica a criação de transação enquanto um cartão está em foco
- **THEN** o entry point recebe o id desse cartão e o formulário abre com ele pré-selecionado; aberto de onde não há cartão em foco, o mesmo entry point é chamado sem o parâmetro e o formulário abre sem pré-seleção

#### Scenario: O foco mudou depois da navegação
- **WHEN** a tela foi aberta por uma rota que nomeia um cartão e o usuário desliza até outro antes de acionar a ação
- **THEN** o formulário abre com o cartão que está em foco, e não com o que a rota nomeia

#### Scenario: Detalhe adaptativo obtido via entry point
- **WHEN** uma feature precisa exibir o detalhe `view*` de outra feature (ex.: o dashboard exibindo o detalhe de uma categoria)
- **THEN** ela injeta o `<Name>Entry` via Koin, obtém o detalhe (um `AdaptiveModal`, tipo de `:core:designsystem`) pela factory `viewXModal()` e o abre via `DetailPaneController`, sem importar nada do `impl` de destino; a superfície painel-vs-sheet é resolvida pela largura da janela na casca

### Requirement: Critério entry point vs core:ui
Componente visual com wiring próprio (ViewModel, use cases) SHALL pertencer a uma feature e ser acessado via entry point. Componente que apenas renderiza modelos de `:core:model` (ex.: `AccountSelector`, `TransactionCard`) SHALL residir em `:core:ui` e ser importado diretamente. Componente de `:core:ui` usado por uma única feature SHOULD migrar para o `impl` dessa feature — mas o critério normativo é o wiring, não a contagem de consumidores.

#### Scenario: Componente burro compartilhado
- **WHEN** duas features renderizam a mesma visualização de um modelo do core, sem estado próprio
- **THEN** o componente reside em `:core:ui` e ambas o importam diretamente, sem entry point

