## 1. Slot de ações no core (designsystem)

- [ ] 1.1 Adicionar `@Composable protected open fun DetailActions() {}` (default vazio) ao `AdaptiveModal` em `AdaptiveDetail.kt`, expondo-o via `RenderContent`/wrapper com `LocalViewModelStoreOwner`
- [ ] 1.2 Reestruturar `DetailPane`: corpo em `Column(weight(1f).verticalScroll)`, remover o `verticalScroll` externo; renderizar `DetailActions()` num rodapé fixo (`background = colorScheme.surface`) somente quando houver ações
- [ ] 1.3 Aplicar elevação/sombra sutil no rodapé fixo condicionada a `scrollState.canScrollForward` (some quando não há conteúdo rolável)
- [ ] 1.4 Ajustar `DetailSheetHost`: renderizar `DetailContent()` + `DetailActions()` no mesmo `Column.verticalScroll`, sem elevação (comportamento mobile inalterado)

## 2. Migrar modais baseados em ViewModel

- [ ] 2.1 `ViewOperationModal` (transactions): mover `EditAndDelete`/bloco de botões e o divisor para `DetailActions()`, lendo `uiState` via `koinViewModel` no novo slot
- [ ] 2.2 `ViewAdjustmentModal` (transactions): mover botões para `DetailActions()`
- [ ] 2.3 `ViewBudgetModal` (budgets): mover botões para `DetailActions()`
- [ ] 2.4 `ViewCategoryModal` (categories): mover botões (Excluir/Editar) e divisor para `DetailActions()`
- [ ] 2.5 `ViewRecurringModal` (recurring): mover botões para `DetailActions()`

## 3. Migrar modal de configurações do widget (dashboard)

- [ ] 3.1 Criar `DashboardComponentOptionsViewModel` que hospeda o estado `config` (recebe `item`/`accounts`/`creditCards`, expõe `config` + update dos toggles) e registrá-lo no módulo Koin do dashboard (`viewModel {}`)
- [ ] 3.2 Migrar `DashboardComponentOptionsModal`: `DetailContent()` lê `config` via `koinViewModel`; mover o `Row` Cancelar/Confirmar e o divisor para `DetailActions()`, com Confirmar disparando `UpdateComponentConfig` e `dismiss`
- [ ] 3.3 Migrar o padding de rodapé (`bottom`) do corpo para o slot de ações, evitando espaçamento duplicado

## 4. Verificação

- [ ] 4.1 Desktop (janela ≥ 840dp): confirmar em cada superfície que as ações ficam fixas no rodapé, corpo rola, e a elevação aparece só com conteúdo rolável (curto = sem flutuar, sem elevação)
- [ ] 4.2 Mobile (janela < 840dp): confirmar que corpo e ações rolam juntos, sem rodapé fixo, como antes
- [ ] 4.3 Cruzar o breakpoint com um detalhe aberto (incl. dashboard) e confirmar que o estado é preservado (sheet↔painel sem reabrir)
- [ ] 4.4 Rodar `./gradlew check` e garantir ausência de erro de rolagem aninhada / medição infinita no painel
