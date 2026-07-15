## MODIFIED Requirements

### Requirement: Tipos de acesso cross-feature à UI
O acesso a recursos de UI de outra feature SHALL ocorrer exclusivamente por: (1) navegação por rota declarada na `api` de destino; (2) modal obtido via entry point; (3) conteúdo `@Composable` retornado por entry point (caso excepcional, apenas mediante necessidade real); (4) registro do subgrafo de navegação da feature de destino via um `register()` no seu entry point que receba o `NavGraphBuilder` como context parameter, quando uma feature hospeda os destinos de outra. Import direto de composable, modal, ViewModel ou função de grafo (`NavGraphBuilder.<nome>Graph()`) de outro `impl` MUST NOT ocorrer. O mecanismo (4) permanece disponível, porém Dashboard e Transactions deixam de ser hospedados: seus `dashboardGraph()`/`transactionsGraph()` são grafos de primeiro nível invocados diretamente pelo `AppNavHost`, como as demais features.

#### Scenario: Abertura de tela de outra feature
- **WHEN** uma feature precisa levar o usuário a uma tela de outra feature
- **THEN** ela navega pela rota da `api` de destino; o composable da tela permanece interno ao `impl` dono

#### Scenario: Dashboard e Transactions como grafos de primeiro nível
- **WHEN** o `AppNavHost` monta a navegação de Dashboard e Transactions
- **THEN** ele invoca `dashboardGraph()` e `transactionsGraph()` diretamente, sem um subgrafo agregador e sem que outra feature os hospede via `register()`

#### Scenario: Feature hospeda os destinos de outra
- **WHEN** uma feature genuinamente hospeda os destinos de outra em seu próprio subgrafo
- **THEN** ela invoca o `register()` do entry point da feature hospedada, obtido do Koin, com o `NavGraphBuilder` como context parameter, e a extensão `NavGraphBuilder.<nome>Graph()` da feature hospedada permanece interna ao seu `impl`

#### Scenario: Ação primária hospedada por outra feature
- **WHEN** o FAB da shell precisa abrir o modal de criação de transação
- **THEN** `shell:impl` obtém o `Modal` por `TransactionsEntry.addTransactionModal()`, sem instanciar `AddTransactionModal` de `transactions:impl`
