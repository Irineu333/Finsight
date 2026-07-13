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

### Requirement: Critério entry point vs core:ui
Componente visual com wiring próprio (ViewModel, use cases) SHALL pertencer a uma feature e ser acessado via entry point. Componente que apenas renderiza modelos de `:core:model` (ex.: `AccountSelector`, `OperationCard`) SHALL residir em `:core:ui` e ser importado diretamente. Componente de `:core:ui` usado por uma única feature SHOULD migrar para o `impl` dessa feature — mas o critério normativo é o wiring, não a contagem de consumidores.

#### Scenario: Componente burro compartilhado
- **WHEN** duas features renderizam a mesma visualização de um modelo do core, sem estado próprio
- **THEN** o componente reside em `:core:ui` e ambas o importam diretamente, sem entry point

