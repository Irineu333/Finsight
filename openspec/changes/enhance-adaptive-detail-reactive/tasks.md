## 1. Observação reativa por id nos repositórios

- [ ] 1.1 Adicionar `observeOperationById(id): Flow<Operation?>` em `IOperationRepository` + impl (query Room dedicada ou derivar de `observeAll*`)
- [ ] 1.2 Adicionar `observe*ById(id): Flow<T?>` para ajuste (se distinto de operação), categoria, orçamento e recorrência nos respectivos repositórios + impls
- [ ] 1.3 Cobrir cada `observe*ById` com teste (emite entidade, re-emite na mudança, emite `null` na exclusão)

## 2. Detalhe de operação (viewTransaction) reativo

- [ ] 2.1 Converter `ViewOperationUiState` em `sealed interface { Loading; Error; Content(operation, perspective) }`
- [ ] 2.2 `ViewOperationViewModel`: construtor por `operationId` + `perspective`; observar por id; rotear `null` (flag `loadedOnce`: primeiro `null` → `Error`, `null` após `Content` → evento de dismiss); `stateIn` com `Loading` inicial
- [ ] 2.3 `ViewOperationModal`: construtor por id/config; renderizar Loading/Error/Content; coletar o evento de dismiss → `LocalDetailPaneController.current.dismiss()`
- [ ] 2.4 Testes do `ViewOperationViewModel` (transições Loading→Content, Content→re-render, primeiro `null`→Error, `null` pós-Content→dismiss)

## 3. Detalhe de ajuste (viewAdjustment) reativo

- [ ] 3.1 `ViewAdjustmentUiState` → `sealed Loading/Error/Content`
- [ ] 3.2 `ViewAdjustmentViewModel`: construtor por id, observação, roteamento do `null`, `Loading` inicial
- [ ] 3.3 `ViewAdjustmentModal`: construtor por id, UI de Loading/Error, coleta do dismiss
- [ ] 3.4 Testes do `ViewAdjustmentViewModel`

## 4. Detalhe de categoria (viewCategory) reativo

- [ ] 4.1 `ViewCategoryUiState` → `sealed Loading/Error/Content`
- [ ] 4.2 `ViewCategoryViewModel`: construtor por id, observação, roteamento do `null`, `Loading` inicial
- [ ] 4.3 `ViewCategoryModal`: construtor por id, UI de Loading/Error, coleta do dismiss
- [ ] 4.4 Testes do `ViewCategoryViewModel`

## 5. Detalhe de orçamento (viewBudget) — criar VM

- [ ] 5.1 Criar `ViewBudgetUiState` (`sealed Loading/Error/Content`) e `ViewBudgetViewModel` (observação por id, roteamento do `null`, `Loading` inicial)
- [ ] 5.2 `ViewBudgetModal`: passar a usar o VM; construtor por id; UI de Loading/Error; coleta do dismiss
- [ ] 5.3 Testes do `ViewBudgetViewModel`

## 6. Detalhe de recorrência (viewRecurring) — criar VM

- [ ] 6.1 Criar `ViewRecurringUiState` (`sealed Loading/Error/Content`) e `ViewRecurringViewModel` (observação por id, roteamento do `null`, `Loading` inicial)
- [ ] 6.2 `ViewRecurringModal`: passar a usar o VM; construtor por id; UI de Loading/Error; coleta do dismiss
- [ ] 6.3 Testes do `ViewRecurringViewModel`

## 7. Entry points de API e call-sites (BREAKING)

- [ ] 7.1 Atualizar assinaturas em `feature/*/api`: `viewOperationModal(operationId, perspective?)`, `viewAdjustmentModal(id)`, `viewCategoryModal(id)`, `viewBudgetModal(id)`, `viewRecurringModal(id)` retornando `AdaptiveModal`
- [ ] 7.2 Atualizar os `*EntryImpl` para construir os modais por id
- [ ] 7.3 Atualizar call-sites cross-feature (`TransactionsScreen`, `AccountsScreen`, `CreditCardsScreen`, `InvoiceTransactionsScreen`, `InstallmentsScreen`, `DashboardComponentContent`) para passar id (+ config)
- [ ] 7.4 Ajustar a navegação detalhe→detalhe (ex.: `OpenRecurring`) para passar id

## 8. Reverter o acoplamento dismissAll → DetailPaneController

- [ ] 8.1 `ModalManager`: remover a composição do `DetailPaneController`; `dismissAll()` limpa apenas `modalState`
- [ ] 8.2 `DesignSystemModule`: voltar a `single { ModalManager() }`
- [ ] 8.3 Ajustar/remover testes que verificavam o teardown do detalhe via `dismissAll()`

## 9. Verificação

- [ ] 9.1 `./gradlew allTests` e `./gradlew check` verdes
- [ ] 9.2 Verificação manual no desktop (janela larga): editar re-renderiza in-place; excluir volta ao empty-state; abrir por id mostra Loading→Content; id inválido mostra Error
- [ ] 9.3 Verificação manual em janela estreita (bottom sheet): mesmos fluxos, com auto-dispensa ao excluir
