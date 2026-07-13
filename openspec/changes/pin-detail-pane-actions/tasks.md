## 1. Slot de ações no core (designsystem)

- [x] 1.1 Adicionar slot de ações ao `AdaptiveModal` como método `@Composable open fun DetailActions()` (default vazio) + `RenderBody`/`RenderActions` com `LocalViewModelStoreOwner` (todos os modais atuais implementam ações; sem flag de presença)
- [x] 1.2 Reestruturar `DetailPane`: corpo em `Column(weight(1f).verticalScroll)`, remover o `verticalScroll` externo; renderizar `RenderActions()` num rodapé fixo `Surface(color = surface)`
- [x] 1.3 Aplicar elevação/sombra sutil no rodapé fixo condicionada a `scrollState.canScrollForward` (some quando não há conteúdo rolável)
- [x] 1.4 Ajustar `DetailSheetHost`: `RenderBody()` + `HorizontalDivider` + `RenderActions()` no mesmo `Column.verticalScroll` (separador é do host: divisor no mobile, sombra no desktop)

## 2. Migrar modais baseados em ViewModel

- [x] 2.1 `ViewOperationModal` (transactions): mover o bloco `EditAndDelete`/mensagem de fatura fechada para `detailActions`, lendo `uiState` via `koinViewModel` no slot; remover o divisor (host provê)
- [x] 2.2 `ViewAdjustmentModal` (transactions): mover botão Excluir para `detailActions`; remover divisor
- [x] 2.3 `ViewBudgetModal` (budgets): mover botões para `detailActions`; remover divisor
- [x] 2.4 `ViewCategoryModal` (categories): mover botões (Excluir/Editar) para `detailActions`; remover divisor
- [x] 2.5 `ViewRecurringModal` (recurring): mover botões para `detailActions`; remover divisor

## 3. Migrar modal de configurações do widget (dashboard)

- [x] 3.1 Hospedar o estado `config` como campo `by mutableStateOf` no próprio `DashboardComponentOptionsModal` (state holder na instância estável, seguindo o precedente do `DetailPaneController`) — **desvio do design**: dispensa criar `DashboardComponentOptionsViewModel` + fiação Koin, evitando complexidade extra
- [x] 3.2 Migrar `DashboardComponentOptionsModal`: `DetailContent()` lê/atualiza `config` do campo; mover o `Row` Cancelar/Confirmar para `detailActions`, com Confirmar disparando `UpdateComponentConfig` e `dismiss`; remover divisor
- [x] 3.3 Migrar o padding de rodapé (`bottom`) do corpo para o slot de ações, evitando espaçamento duplicado

## 4. Verificação

- [ ] 4.1 Desktop (janela ≥ 840dp): confirmar em cada superfície que as ações ficam fixas no rodapé, corpo rola, e a elevação aparece só com conteúdo rolável (curto = sem flutuar, sem elevação)
- [ ] 4.2 Mobile (janela < 840dp): confirmar que corpo e ações rolam juntos, sem rodapé fixo, como antes
- [ ] 4.3 Cruzar o breakpoint com um detalhe aberto (incl. dashboard) e confirmar que o estado é preservado (sheet↔painel sem reabrir)
- [x] 4.4 Compilar os módulos afetados (`:core:designsystem` + os 5 feature impls) via `compileDebugKotlinAndroid` — **BUILD SUCCESSFUL** (`check` completo pulado a pedido; validação de rolagem aninhada/medição infinita fica para a verificação manual 4.1)
