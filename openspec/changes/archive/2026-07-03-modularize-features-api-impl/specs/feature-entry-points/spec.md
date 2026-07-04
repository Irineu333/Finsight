# Spec: feature-entry-points

## ADDED Requirements

### Requirement: Entry point único por feature
Cada feature que expõe UI a outras features SHALL declarar na sua `api` uma interface única `<Nome>Entry` agrupando essa superfície pública. O `impl` SHALL implementá-la e registrá-la no módulo Koin da feature. As assinaturas do entry point SHALL referenciar apenas tipos de `:core:*` (modelos de `:core:model`, `Modal` de `:core:designsystem`).

#### Scenario: Consumo de modal de outra feature
- **WHEN** `dashboard:impl` precisa exibir o modal de pagamento de fatura de creditcards
- **THEN** ele injeta `CreditCardsEntry` via Koin, obtém o `Modal` pelo método do entry point e o exibe via `ModalManager`, sem importar nada de `creditcards:impl`

#### Scenario: Feature sem UI pública
- **WHEN** nenhuma outra feature consome UI da feature
- **THEN** a feature não declara entry point (a interface não é criada preventivamente)

### Requirement: Tipos de acesso cross-feature à UI
O acesso a recursos de UI de outra feature SHALL ocorrer exclusivamente por: (1) navegação por rota declarada na `api` de destino; (2) modal obtido via entry point; (3) conteúdo `@Composable` retornado por entry point (caso excepcional, apenas mediante necessidade real). Import direto de composable, modal ou ViewModel de outro `impl` MUST NOT ocorrer.

#### Scenario: Abertura de tela de outra feature
- **WHEN** uma feature precisa levar o usuário a uma tela de outra feature
- **THEN** ela navega pela rota da `api` de destino; o composable da tela permanece interno ao `impl` dono

### Requirement: Critério entry point vs core:ui
Componente visual com wiring próprio (ViewModel, use cases) SHALL pertencer a uma feature e ser acessado via entry point. Componente que apenas renderiza modelos de `:core:model` (ex.: `AccountSelector`, `OperationCard`) SHALL residir em `:core:ui` e ser importado diretamente. Componente de `:core:ui` usado por uma única feature SHOULD migrar para o `impl` dessa feature — mas o critério normativo é o wiring, não a contagem de consumidores.

#### Scenario: Componente burro compartilhado
- **WHEN** duas features renderizam a mesma visualização de um modelo do core, sem estado próprio
- **THEN** o componente reside em `:core:ui` e ambas o importam diretamente, sem entry point
