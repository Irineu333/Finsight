## Why

Os entries de modal em `:feature:*:api` recebem hoje uma mistura inconsistente de modelos de domínio ricos (`Invoice`, `Operation`, `Category`, `Recurring`, `BudgetProgress`, `Account`) e parâmetros transientes. Vários ViewModels já re-buscam a entidade pelo id internamente (`getCategoryById`, `getOperationById`, `getCreditCardById`), tratando o model passado apenas como fallback de estado inicial — o id é a fonte da verdade implícita.

Isso gera três problemas:

1. **Vazamento de tipos de domínio através de `:api`** — features acabam exportando models só para serem aceitos como argumento de modal, aumentando o acoplamento entre `:api`s.
2. **Staleness silencioso** — o caller pode segurar um snapshot desatualizado da entidade entre o momento em que abriu a tela e o momento em que abriu o modal.
3. **Inconsistência de contrato** — `CloseInvoiceModalEntry(invoiceId, closingDate)` já segue o padrão "id + transientes"; os outros 13 entries seguem o padrão de model rico.

## What Changes

- **BREAKING** Entries de modal em `:feature:*:api` passam a receber **ids** (e parâmetros transientes não-entidade) em vez de modelos de domínio.
- **BREAKING** UiStates dos modais migram para o padrão `sealed class { Loading, Content [, Empty] }` consistente com `AccountsUiState`/`CreditCardsUiState`/`EditInvoiceBalanceUiState`. ViewModels carregam a entidade por id e emitem `Empty` quando a entidade foi deletada (race).
- **BREAKING** `PayInvoiceModalEntry` e `AdvancePaymentModalEntry` deixam de aceitar `currentBillAmount` — o VM passa a calcular via `CalculateInvoiceUseCase` para garantir saldo fresh.
- **BREAKING** `ViewBudgetModalEntry` aceita `budgetId` em vez de `BudgetProgress` — o VM reconstrói o progresso internamente via use case.
- Form modals em modo criação (id == null) continuam renderizando `Content` no primeiro frame — `Loading` só vale em modo edição com fetch real.

## Capabilities

### New Capabilities

- `modal-entries`: Contrato de entrada para modais expostos por `:feature:*:api`. Define o padrão "id-only" (entries recebem `Long` ids e parâmetros transientes), o ciclo `Loading → Content | Empty` para hidratação por id, a regra de `Loading` apenas em edit-mode para forms, e a proibição de valores derivados nos parâmetros.

### Modified Capabilities

*(nenhuma — mudanças não afetam capabilities existentes do projeto)*

## Impact

**Entries afetados em `:api` (assinaturas):**

| Entry | Antes | Depois |
| --- | --- | --- |
| `EditInvoiceBalanceModalEntry` | `Invoice` | `Long` |
| `PayInvoiceModalEntry` | `Invoice, Double` | `Long` |
| `AdvancePaymentModalEntry` | `Invoice, Double` | `Long` |
| `CreditCardFormModalEntry` | `CreditCard?` | `Long?` |
| `ViewOperationModalEntry` | `Operation, OperationPerspective?` | `Long, OperationPerspective?` |
| `ViewAdjustmentModalEntry` | `Operation` | `Long` |
| `ViewCategoryModalEntry` | `Category` | `Long` |
| `CategoryFormModalEntry` | `Category?, Category.Type?` | `Long?, Category.Type?` |
| `ViewBudgetModalEntry` | `BudgetProgress` | `Long` |
| `ViewRecurringModalEntry` | `Recurring` | `Long` |
| `RecurringFormModalEntry` | `Recurring?` | `Long?` |
| `ConfirmRecurringModalEntry` | `Recurring, LocalDate` | `Long, LocalDate` |
| `AccountFormModalEntry` | `Account?` | `Long?` |
| `CloseInvoiceModalEntry` | `Long, LocalDate` | (sem mudança — referência) |

**Imports removidos de `:api`:** `feature/*/model/*` em todos os entries; permanecem apenas `OperationPerspective` e `Category.Type` (tipos de seleção, não entidades).

**ViewModels e UiStates afetados em `:impl`:** ~13 ViewModels e ~11 UiStates migram para o padrão sealed; ViewModels passam a receber id no construtor e a fazer fetch via repository.

**Call-sites:** todos os `entry.create(model, ...)` em screens/VMs viram `entry.create(model.id, ...)`. Espalhado mas trivial.

**Sem mudança:**
- Layout, comportamento de produto e textos dos modais.
- `OperationPerspective`, `Category.Type`, `closingDate`, `targetDate`, `initialType` continuam como parâmetros (não são entidades persistentes).
- Sistema de DI (Koin) e injeção via `parametersOf(...)`.
