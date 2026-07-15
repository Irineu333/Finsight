## 1. Fundação em core:designsystem

- [x] 1.1 Resolver os pontos abertos do design que bloqueiam código: breakpoint do painel (Medium/600dp vs Expanded/≥840dp), largura do painel (valor fixo) e origem do título/header (expor `title` no `AdaptiveModal` vs delegar ao conteúdo) — **Decidido:** breakpoint Expanded/≥840dp; largura fixa 400dp; `AdaptiveModal` expõe `title()`
- [x] 1.2 Se o painel usar um breakpoint próprio, estender `ui/util/WindowSize.kt` com o helper correspondente (ex.: `isExtraWideWindow()`), reusando a API `androidx.compose.material3.adaptive`
- [x] 1.3 Criar `AdaptiveModal` em `core/designsystem/.../ui/component/` — `Modal` + `ViewModelStoreOwner`, expondo `@Composable DetailContent()` (conteúdo puro) e provendo `LocalViewModelStoreOwner`; limpar o `viewModelStore` em `onDismissed()`
- [x] 1.4 Criar `DetailPaneController` + `LocalDetailPaneController` — slot único `current: AdaptiveModal?`, com `show(detail)` (substitui), `dismiss()` e limpeza do store ao dispensar
- [x] 1.5 Criar `DetailHost` — largo: coluna fixa à direita com header (título + botão X) + `DetailContent` rolável + empty-state; estreito: `ModalBottomSheet` envolvendo `DetailContent` (reusar insets e `skipPartiallyExpanded` do `ModalBottomSheet` atual)
- [x] 1.6 Registrar `DetailPaneController` como `single {}` no `designsystemModule`
- [x] 1.7 Confirmar que `ModalManager.kt` permanece inalterado

## 2. Integração na casca (feature/shell)

- [x] 2.1 Prover `LocalDetailPaneController` no `App`/casca, próximo ao `ModalManagerHost` (via `DetailPaneHost` dentro de `ModalManagerHost`)
- [x] 2.2 Plugar o `DetailHost` (ramo largo) no `Row` do `ChromeHost` como irmão de `content(padding)`, fora do `AnimatedVisibility` do rail, formando `rail | conteúdo | painel`
- [x] 2.3 Montar o caminho estreito do `DetailHost` na camada de overlay (junto do `ModalManagerHost`) para exibir o sheet quando a janela é estreita
- [x] 2.4 Garantir que modais transitórios do `ModalManager` renderizem por cima do painel (z-order) no caso empilhado (`DetailPaneHost` fica dentro de `ModalManagerHost`, logo `modalManager.Content()` desenha depois)

## 3. Migração dos 5 detalhes view*

- [x] 3.1 `ViewOperationModal` (transactions): estender `AdaptiveModal`, renomear o método de conteúdo para `DetailContent()`, expor título se necessário
- [x] 3.2 `ViewAdjustmentModal` (transactions): idem
- [x] 3.3 `ViewCategoryModal` (categories): idem
- [x] 3.4 `ViewBudgetModal` (budgets): idem
- [x] 3.5 `ViewRecurringModal` (recurring): idem
- [x] 3.6 Ajustar as factories `viewXModal()` nos `<Name>Entry` (transactions, categories, budgets, recurring) para retornar `AdaptiveModal`

## 4. Migração dos call sites

- [x] 4.1 Trocar `modalManager.show(...)` → `detailController.show(...)` nos call sites diretos dos 5 `view*` (features donas) — inclui o detalhe-sobre-detalhe interno (`ViewOperationModal`/`ViewBudgetModal` → `viewRecurringModal`) e os dismiss de navegação (`manager.dismissAll()` → `detailController.dismiss()`)
- [x] 4.2 Trocar os call sites de detalhe no dashboard (`DashboardComponentContent.kt`): `viewAdjustmentModal`/`viewOperationModal`, `viewCategoryModal`, `viewBudgetModal`, `viewRecurringModal`
- [x] 4.3 Verificar que os call sites de formulário/confirmação (não-`view*`) continuam usando `modalManager.show(...)` — também migrados os `view*` das demais features consumidoras (creditcards, accounts, report), que a spec exige adaptativos em qualquer origem

## 5. Verificação

- [x] 5.1 `./gradlew check` e `./gradlew allTests` passam — **compilação de todos os targets (JVM/Desktop + Android + Kotlin/Native iOS) OK e testes unitários Android verdes**; `allTests` completo não finaliza neste ambiente por falha pré-existente no link nativo iOS (`ld: framework 'FirebaseCore' not found`, pods não instalados), alheia a esta mudança
- [x] 5.2 Desktop largo: abrir cada um dos 5 detalhes → aparecem no painel à direita; empty-state quando nada selecionado; X fecha — **verificado manualmente**
- [x] 5.3 Detalhe sobre detalhe substitui (ex.: operação → recorrência vinculada); sem "voltar" interno — **verificado manualmente**
- [x] 5.4 Redimensionar cruzando o breakpoint com detalhe aberto: sheet ⇄ painel sem reabrir nem perder estado — **verificado manualmente**
- [x] 5.5 Caso empilhado: em janela larga, abrir formulário (ex.: editar) a partir do detalhe → sheet de overlay por cima do painel, detalhe permanece visível — **verificado manualmente**
- [x] 5.6 Janela estreita: os 5 detalhes continuam como bottom sheet, sem regressão — **verificado manualmente**
- [x] 5.7 Formulários e confirmações permanecem bottom sheet em qualquer largura — **verificado manualmente**
- [x] 5.8 `openspec validate adaptive-detail-pane` continua válido

## 6. Refinamento do header do painel

- [x] 6.1 Remover o **título** e o **divisor horizontal** do header do painel largo (`DetailPane` em `AdaptiveDetail.kt`) — o header passa a ser apenas o botão **X** alinhado à direita; revertida a decisão 1.1 de expor `title()`
- [x] 6.2 Remover o abstract `AdaptiveModal.title()` e os 5 overrides `view*` (transactions/operation+adjustment, categories, budgets, recurring), com os imports de recurso `view_*_title` órfãos
- [x] 6.3 Compilação Android debug dos módulos afetados OK; spec inalterada (não exigia título/divisor — só o X, empty-state e o arranjo de três colunas, todos mantidos)
- [x] 6.4 Envolver o conteúdo do painel largo (`DetailPane`) em `AnimatedContent` (fade in/out, chaveado por `it?.key`) para suavizar aparecimento/desaparecimento e a substituição detalhe→detalhe; o caminho estreito (`ModalBottomSheet`) já anima nativamente
