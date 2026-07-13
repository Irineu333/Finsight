## 1. Fundação em core:designsystem

- [ ] 1.1 Resolver os pontos abertos do design que bloqueiam código: breakpoint do painel (Medium/600dp vs Expanded/≥840dp), largura do painel (valor fixo) e origem do título/header (expor `title` no `AdaptiveModal` vs delegar ao conteúdo)
- [ ] 1.2 Se o painel usar um breakpoint próprio, estender `ui/util/WindowSize.kt` com o helper correspondente (ex.: `isExtraWideWindow()`), reusando a API `androidx.compose.material3.adaptive`
- [ ] 1.3 Criar `AdaptiveModal` em `core/designsystem/.../ui/component/` — `Modal` + `ViewModelStoreOwner`, expondo `@Composable DetailContent()` (conteúdo puro) e provendo `LocalViewModelStoreOwner`; limpar o `viewModelStore` em `onDismissed()`
- [ ] 1.4 Criar `DetailPaneController` + `LocalDetailPaneController` — slot único `current: AdaptiveModal?`, com `show(detail)` (substitui), `dismiss()` e limpeza do store ao dispensar
- [ ] 1.5 Criar `DetailHost` — largo: coluna fixa à direita com header (título + botão X) + `DetailContent` rolável + empty-state; estreito: `ModalBottomSheet` envolvendo `DetailContent` (reusar insets e `skipPartiallyExpanded` do `ModalBottomSheet` atual)
- [ ] 1.6 Registrar `DetailPaneController` como `single {}` no `designsystemModule`
- [ ] 1.7 Confirmar que `ModalManager.kt` permanece inalterado

## 2. Integração na casca (feature/shell)

- [ ] 2.1 Prover `LocalDetailPaneController` no `App`/casca, próximo ao `ModalManagerHost`
- [ ] 2.2 Plugar o `DetailHost` (ramo largo) no `Row` do `ChromeHost` como irmão de `content(padding)`, fora do `AnimatedVisibility` do rail, formando `rail | conteúdo | painel`
- [ ] 2.3 Montar o caminho estreito do `DetailHost` na camada de overlay (junto do `ModalManagerHost`) para exibir o sheet quando a janela é estreita
- [ ] 2.4 Garantir que modais transitórios do `ModalManager` renderizem por cima do painel (z-order) no caso empilhado

## 3. Migração dos 5 detalhes view*

- [ ] 3.1 `ViewOperationModal` (transactions): estender `AdaptiveModal`, renomear o método de conteúdo para `DetailContent()`, expor título se necessário
- [ ] 3.2 `ViewAdjustmentModal` (transactions): idem
- [ ] 3.3 `ViewCategoryModal` (categories): idem
- [ ] 3.4 `ViewBudgetModal` (budgets): idem
- [ ] 3.5 `ViewRecurringModal` (recurring): idem
- [ ] 3.6 Ajustar as factories `viewXModal()` nos `<Name>Entry` (transactions, categories, budgets, recurring) para retornar `AdaptiveModal`

## 4. Migração dos call sites

- [ ] 4.1 Trocar `modalManager.show(...)` → `detailController.show(...)` nos call sites diretos dos 5 `view*` (features donas)
- [ ] 4.2 Trocar os call sites de detalhe no dashboard (`DashboardComponentContent.kt`): `viewAdjustmentModal`/`viewOperationModal`, `viewCategoryModal`, `viewBudgetModal`, `viewRecurringModal`
- [ ] 4.3 Verificar que os call sites de formulário/confirmação (não-`view*`) continuam usando `modalManager.show(...)`

## 5. Verificação

- [ ] 5.1 `./gradlew check` e `./gradlew allTests` passam
- [ ] 5.2 Desktop largo: abrir cada um dos 5 detalhes → aparecem no painel à direita; empty-state quando nada selecionado; X fecha
- [ ] 5.3 Detalhe sobre detalhe substitui (ex.: operação → recorrência vinculada); sem "voltar" interno
- [ ] 5.4 Redimensionar cruzando o breakpoint com detalhe aberto: sheet ⇄ painel sem reabrir nem perder estado
- [ ] 5.5 Caso empilhado: em janela larga, abrir formulário (ex.: editar) a partir do detalhe → sheet de overlay por cima do painel, detalhe permanece visível
- [ ] 5.6 Janela estreita: os 5 detalhes continuam como bottom sheet, sem regressão
- [ ] 5.7 Formulários e confirmações permanecem bottom sheet em qualquer largura
- [ ] 5.8 `openspec validate adaptive-detail-pane` continua válido
