# Modal Entries

## Purpose

Definir o contrato de entrada para modais expostos por `:feature:*:api`. Estabelece que entries recebem ids (não modelos de domínio), padroniza UiStates como sealed `Loading | Content [| Empty]`, define o comportamento de carregamento por id e o tratamento de entidade não-encontrada.

## Requirements

### Requirement: Modal entries recebem ids para entidades persistentes

Toda função `create(...)` em modal entry de `:feature:*:api` SHALL aceitar `Long` ids para entidades persistentes. Modelos de domínio (`Invoice`, `Operation`, `Category`, `Recurring`, `Account`, `BudgetProgress`, `CreditCard`) MUST NOT aparecer na assinatura.

#### Scenario: Entry recebe id em vez de model

- **WHEN** um desenvolvedor define um modal entry para visualizar uma categoria
- **THEN** a assinatura MUST ser `create(categoryId: Long): ModalBottomSheet` e MUST NOT ser `create(category: Category): ModalBottomSheet`

#### Scenario: Form entry com id nullable representa criação vs edição

- **WHEN** um modal entry suporta tanto criação quanto edição
- **THEN** a assinatura MUST usar id nullable (ex: `create(accountId: Long? = null)`) onde `null` significa criação

#### Scenario: Parâmetros não-entidade são permitidos

- **WHEN** um modal precisa de contexto adicional não-persistente (datas, perspectivas, tipos de seleção)
- **THEN** esses parâmetros MUST ser primitivos ou tipos enum/sealed que NÃO sejam entidades persistentes (ex: `LocalDate`, `OperationPerspective`, `Category.Type`)

---

### Requirement: UiStates de modais id-driven seguem padrão sealed

Modais cujo VM carrega entidade por id SHALL ter UiState `sealed` com pelo menos `Loading` e `Content`. `Empty` MUST ser adicionado quando o modal exibe mensagem em vez de fechar ao receber id inexistente.

#### Scenario: View modal tem Loading, Content e Empty

- **WHEN** um view modal recebe um id que pode apontar para entidade deletada
- **THEN** seu UiState MUST ter `Loading`, `Content(model)` e `Empty`

#### Scenario: Action modal tem apenas Loading e Content

- **WHEN** um modal de ação (pay, confirm, edit balance) recebe um id e fecha em caso de entidade deletada
- **THEN** seu UiState MUST ter `Loading` e `Content`, e o VM MUST chamar `modalManager.dismiss()` se a entidade não existir

---

### Requirement: Form modals em modo criação não passam por Loading

Modal forms com id nullable SHALL emitir `Content` no estado inicial quando `id == null`. `Loading` SHALL ser usado apenas quando há fetch real (modo edição com `id != null`).

#### Scenario: Criação inicia em Content

- **WHEN** um form modal é aberto com `id == null` (criação)
- **THEN** seu UiState inicial MUST ser `Content` com valores default

#### Scenario: Edição inicia em Loading

- **WHEN** um form modal é aberto com `id != null` (edição)
- **THEN** seu UiState inicial MUST ser `Loading` até o repo retornar a entidade, então transitar para `Content(prefilled)`

---

### Requirement: ViewModels resolvem entidades sempre via id, não recebem snapshots

ViewModels de modais SHALL receber ids como parâmetros de construtor e MUST resolver a entidade via repository. Models de domínio MUST NOT ser parâmetros de construtor de ViewModels para modais id-driven.

#### Scenario: VM busca entidade por id

- **WHEN** o ViewModel é construído via Koin com `parametersOf(id)`
- **THEN** o VM MUST chamar `repository.getXxxById(id)` para obter o estado canônico

#### Scenario: VM trata id deletado conforme tipo de modal

- **WHEN** `getXxxById(id)` retorna null
- **THEN** view modals MUST emitir `Empty`; action modals e form modals em edit-mode MUST chamar `modalManager.dismiss()` e `Crashlytics.recordException`

---

### Requirement: Valores derivados não são parâmetros de entry

Valores que podem ser computados a partir do id (saldos, totais, progresso) MUST NOT ser parâmetros do entry. ViewModels SHALL recomputar via use cases.

#### Scenario: Saldo de fatura é recomputado pelo VM

- **WHEN** `PayInvoiceModalEntry.create(invoiceId)` é chamado
- **THEN** o VM MUST chamar `CalculateInvoiceUseCase(invoiceId)` em vez de receber `currentBillAmount` como parâmetro

#### Scenario: Progresso de orçamento é reconstruído pelo VM

- **WHEN** `ViewBudgetModalEntry.create(budgetId)` é chamado
- **THEN** o VM MUST reconstruir `BudgetProgress` via use case em vez de recebê-lo como parâmetro

---

### Requirement: Loading state preserva dimensões do modal

O conteúdo emitido durante o estado `Loading` SHALL ocupar dimensões compatíveis com as do `Content` final, evitando layout shift no `ModalBottomSheet`.

#### Scenario: Loading não causa salto de altura

- **WHEN** um modal transita de `Loading` para `Content`
- **THEN** a altura do bottom sheet MUST permanecer estável (sem reanimação de half-expanded para expanded), tipicamente via uma `Column` placeholder com title + `CircularProgressIndicator` dentro do mesmo padding/estrutura do `Content`
