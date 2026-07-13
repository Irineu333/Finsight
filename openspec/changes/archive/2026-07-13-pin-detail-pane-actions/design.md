## Context

As superfícies adaptativas (`AdaptiveModal` em `core/designsystem/.../AdaptiveDetail.kt`) renderam um único `DetailContent()` que hoje mistura corpo e botões de ação num mesmo `Column`. A rolagem é responsabilidade do container:

- **Painel** (`DetailPane`, janela ≥ 840dp): envolve `RenderContent()` num `Column.verticalScroll` → as ações rolam junto e, com conteúdo curto, ficam no meio de um painel de altura cheia.
- **Bottom sheet** (`DetailSheetHost`, janela < 840dp): também envolve num `Column.verticalScroll` → ações rolam junto, o que é o comportamento desejado no mobile.

Existem 6 superfícies adaptativas. Cinco (`ViewOperationModal`, `ViewAdjustmentModal`, `ViewBudgetModal`, `ViewCategoryModal`, `ViewRecurringModal`) obtêm estado via `koinViewModel`. Uma (`DashboardComponentOptionsModal`) mantém estado local `var config by remember { mutableStateOf(...) }` cujo botão Confirmar depende.

## Goals / Non-Goals

**Goals:**
- Fixar as ações no rodapé do painel (desktop), rolando apenas o corpo.
- Separar o rodapé fixo do corpo por elevação/sombra sutil quando houver conteúdo rolável por baixo.
- Preservar o comportamento atual do bottom sheet (corpo + ações rolam juntos).
- Não introduzir branching de plataforma nas features — a decisão de arranjo fica na casca/host.

**Non-Goals:**
- Mudar quais superfícies são adaptativas ou a decisão painel-vs-sheet por largura.
- Alterar rotas, navegação, `ModalManager` ou o ciclo de vida do `DetailPaneController`.
- Rever visual dos botões em si (cores, formas) além de movê-los para o slot.

## Decisions

### D1 — Slot de ações separado no `AdaptiveModal`
Adicionar `@Composable protected open fun DetailActions() = Unit` (default vazio) ao lado do já existente `DetailContent()`. `RenderBody()`/`RenderActions()` provêm `LocalViewModelStoreOwner`; a casca decide como arranjar corpo e ações. Como todas as superfícies atuais implementam ações, o rodapé é sempre renderizado — sem flag de presença.

- **Por que dois slots (e não um `DetailScaffold` com closures):** decisão do usuário; espelha o slot abstrato já existente (`ColumnScope.BottomSheetContent`), é mais descobrível e mantém o layout na casca (host), sem cada modal ter que arranjar corpo/rodapé.
- **Por que método (e não `(@Composable () -> Unit)?` nulável):** manter a simetria com `DetailContent()` (também método).
- **Alternativa descartada:** `DetailScaffold` reutilizável usado por dentro de cada modal com corpo/rodapé por closure — resolveria o estado compartilhado sem hoisting, mas empurra o arranjo para dentro de cada feature.

### D2 — Estado compartilhado corpo↔ações
`DetailContent()` e `DetailActions()` são composables irmãos chamados pela casca, então não compartilham `remember`. Como o `AdaptiveModal` **é** o `ViewModelStoreOwner` provido em `RenderContent()`, chamar `koinViewModel<T>()` nos dois slots devolve a **mesma** instância → os 5 modais baseados em ViewModel dividem-se sem tocar em estado.

Para o `DashboardComponentOptionsModal`, elevar o estado `config` para um `DashboardComponentOptionsViewModel` (Koin `viewModel {}`), que:
- recebe `item`/`accounts`/`creditCards` por parâmetro,
- expõe `config` como estado e um `onAction`/`update` para os toggles,
- é lido por `koinViewModel` tanto no corpo quanto nas ações.

- **Por que ViewModel:** uniformiza os 6 modais e segue a convenção do projeto (`viewModel {}` para telas/superfícies). Alternativa (state holder passado no construtor) foi descartada por divergir do padrão dos outros 5.

### D3 — Layout na casca
- **`DetailPane`** (desktop): `Column(fillMaxHeight) { close(X); Column(weight(1f).verticalScroll){ DetailContent() }; actionsFooter { DetailActions() } }`. O rodapé só é renderizado se a superfície tiver ações; recebe `background = colorScheme.surface` e **elevação/sombra** condicionada a `scrollState.canScrollForward` (há conteúdo por baixo). A remoção do `verticalScroll` externo evita rolagem aninhada com o `weight(1f)`.
- **`DetailSheetHost`** (mobile): mantém `Column.verticalScroll { DetailContent(); DetailActions() }` — ações inline, elevação não se aplica.

- **Elevação reativa ao scroll (`canScrollForward`)** foi escolhida em vez de sombra sempre-visível: some quando não há o que rolar, evitando "moldura" desnecessária com conteúdo curto (que é justamente o caso do bug atual).

### D4 — Migração dos 6 modais
Mover o bloco de botões (e o `HorizontalDivider` que hoje o antecede) do fim de `DetailContent()` para o override `DetailActions()`. O divisor deixa de ser necessário como separador no painel (a elevação cumpre o papel); manter/remover o divisor no rodapé é detalhe visual a decidir na implementação, mantendo consistência entre os 6.

## Risks / Trade-offs

- **Rolagem aninhada / medição infinita** ao combinar `weight(1f)` + `verticalScroll` dentro do painel `fillMaxHeight` → mitigar garantindo que o corpo fique em `weight(1f)` (altura limitada) e que o `verticalScroll` externo seja removido do `DetailPane`.
- **Regressão no mobile** ao mexer no host compartilhado → o `DetailSheetHost` continua com corpo+ações no mesmo scroll; cobrir com verificação visual em janela estreita.
- **Estado do dashboard ao cruzar o breakpoint** → o ViewModel vive no `viewModelStore` do `AdaptiveModal` (limpo só em `onDismissed`), preservando `config` na transição sheet↔painel, coerente com o requisito de preservação de estado existente.
- **Padding do rodapé** hoje embutido no `Column` do corpo (`padding(bottom = 32.dp)`) → migrar o padding relevante para o slot de ações para não duplicar espaçamento.

## Migration Plan

1. `core:designsystem`: adicionar `DetailActions()`, reestruturar `DetailPane`, ajustar `DetailSheetHost`.
2. Migrar os 5 modais baseados em ViewModel (mover botões → `DetailActions`).
3. Criar `DashboardComponentOptionsViewModel`, registrar no módulo Koin do dashboard, migrar o modal.
4. Verificar desktop (rodapé fixo + elevação reativa) e mobile (rolagem conjunta) em cada superfície.

Rollback: mudança é aditiva no `AdaptiveModal` (slot default vazio) e localizada; reverter os commits restaura o comportamento anterior sem migração de dados.

## Open Questions

- Manter o `HorizontalDivider` dentro do slot de ações, ou confiar apenas na elevação para separar? (Preferência inicial: só elevação no painel, para não duplicar separadores.)
