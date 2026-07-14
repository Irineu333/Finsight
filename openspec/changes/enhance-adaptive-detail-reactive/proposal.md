## Why

Hoje os detalhes `view*` são **renderizados por snapshot**: o modal recebe o objeto de domínio já carregado e o ViewModel faz um `flow { emit(getById(id) ?: seed) }` **one-shot**, sem re-observar. Como o detalhe nunca reage à mudança da entidade, editar um item deixa o detalhe **stale** — e a solução vigente (`fix-stale-ui-after-delete`) foi acoplar `ModalManager.dismissAll()` ao `DetailPaneController` para **fechar** o detalhe ao salvar/deletar, em vez de atualizá-lo. Aquele change registrou explicitamente como débito adiado: "re-observar o detalhe in-place fica como melhoria futura fora de escopo". Esta proposta paga esse débito: torna os detalhes **reativos por id**, para que edições re-renderizem in-place e exclusões se auto-dispensem de forma dirigida.

## What Changes

- **Detalhes passam a receber apenas o `id` (+ config não-recuperável), nunca o objeto de domínio.** **BREAKING** nas assinaturas dos entry points de `feature/*/api` (`viewOperationModal`, `viewAdjustmentModal`, `viewCategoryModal`, `viewBudgetModal`, `viewRecurringModal`) e em todos os call-sites cross-feature.
- **Cada `view*` observa a entidade por id** (`observe<Entity>ById(id): Flow<T?>`, novo nos repositórios/DAOs afetados) e reage às mudanças — editar re-renderiza o detalhe in-place, sem fechá-lo.
- **Cada `view*` ViewModel emite um `sealed UiState` com `Loading | Error | Content`** (sem classe base — padrão repetido por convenção em cada VM). `Loading` é o estado inicial; `Error` cobre a falha de obtenção do objeto; `Content` re-emite a cada mudança.
- **Roteamento do `null` por VM:** primeira emissão `null` → `Error`; `null` após já ter tido `Content` (entidade deletada com detalhe aberto) → auto-dispensa o detalhe via evento (`controller.dismiss()`), voltando ao empty-state.
- **`viewRecurring` e `viewBudget` ganham ViewModel** (hoje renderizam objeto puro sem VM), padronizando os 5 detalhes.
- **Reverter o acoplamento `ModalManager.dismissAll() → DetailPaneController.dismiss()`:** com o auto-dismiss reativo cobrindo a exclusão, `dismissAll()` volta a limpar **apenas** a pilha de modais transitórios. Salvar uma edição deixa de fechar o detalhe.
- **Fora de escopo:** `DashboardComponentOptionsModal` (configurações de widget) — é estado de UI de edição do dashboard, não entidade de domínio observável por id; permanece como está.

## Capabilities

### New Capabilities
<!-- Nenhuma capability nova; o comportamento adaptativo já é coberto por adaptive-detail-pane. -->

### Modified Capabilities
- `adaptive-detail-pane`: os detalhes `view*` passam a ser dirigidos por id e reativos, com apresentação de `Loading`/`Error`/`Content`; edições re-renderizam o detalhe in-place em vez de fechá-lo; exclusões auto-dispensam o detalhe pela observação reativa do `null`; e o teardown `dismissAll()` deixa de alcançar o `DetailPaneController`.

## Impact

- **`core/*` (repositórios/DAOs das 5 entidades):** novo `observe<Entity>ById(id): Flow<T?>` — operação, ajuste, categoria, orçamento, recorrência. Deriva de fonte Room (query direta ou `observeAll().map { find }`).
- **`feature/transactions/impl`** — `ViewOperationViewModel` + `ViewAdjustmentViewModel`: `sealed UiState`, observação por id, roteamento de `null`, evento de dismiss. `ViewOperationModal`/`ViewAdjustmentModal`: UI de Loading/Error + construtor por id.
- **`feature/categories/impl`** — `ViewCategoryViewModel`/`ViewCategoryModal`: idem.
- **`feature/budgets/impl`** — `ViewBudgetModal` ganha `ViewBudgetViewModel` + `UiState`.
- **`feature/recurring/impl`** — `ViewRecurringModal` ganha `ViewRecurringViewModel` + `UiState`.
- **`feature/*/api`** — assinaturas dos entry points `view*Modal(...)` passam a receber `id` (+ config). **BREAKING**.
- **Call-sites cross-feature** — `TransactionsScreen`, `AccountsScreen`, `CreditCardsScreen`, `InvoiceTransactionsScreen`, `InstallmentsScreen`, `DashboardComponentContent` passam a construir os detalhes por id.
- **`core/designsystem`** — `ModalManager.dismissAll()` deixa de chamar `detailPaneController.dismiss()`; DI de `ModalManager` volta a não depender do `DetailPaneController`.
- **Sem novas dependências.** Sem mudanças nos ~21 `DeleteXxxViewModel`.
