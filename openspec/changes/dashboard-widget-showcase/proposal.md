## Why

O modo de edição do dashboard é uma única lista: os widgets ativos em cima, um cabeçalho
"Disponíveis" no meio e os desativados embaixo, apagados. Ativar e desativar **são** reordenar —
arrastar para baixo do cabeçalho desativa —, e o cabeçalho e os dois vazios existem como
chaves-sentinela só para servirem de alvo de drop (`DashboardEditLayout.kt:22-63`). Com catorze
tipos de widget renderizados em tamanho real, metade da tela é o que *não* está no dashboard, e a
semântica "acima ou abaixo de uma linha" mistura duas perguntas que o usuário faz separadamente:
**o que** entra e **em que ordem** fica.

Esta mudança separa as duas: a lista passa a ter só o que está no dashboard, e o que pode entrar
mora numa **vitrine** à parte — uma sheet no celular, o painel lateral no desktop —, com o arrasto
como único verbo entre as duas. É também a base para permitir o mesmo widget mais de uma vez, que
fica para uma mudança própria (ver Impact).

## What Changes

- **A lista do editor tem só os widgets ativos.** Some a seção "Disponíveis", somem o cabeçalho e
  os dois placeholders, e com eles as três chaves-sentinela e a aritmética de fronteira.
- **Uma vitrine oferece os widgets que não estão no dashboard.** É o catálogo menos os ativos,
  derivado — não guardado —, sem os tipos deprecados (regra já em `dashboard-balance-widgets`).
  Cada card é o widget verdadeiro com dados fictícios, inerte, com uma alça de arrasto imediata.
- **Três gestos, um motor.** Arrastar da vitrine para a lista adiciona o widget na posição do slot
  aberto; arrastar da lista para a vitrine remove; arrastar dentro da lista reordena. Não há botão
  de adicionar nem de remover. **BREAKING** para quem usava os comandos em massa: "Adicionar todos"
  e "Remover todos" saem.
- **A vitrine nunca é modal.** No celular é uma sheet composta **na mesma árvore** da tela
  (`BottomSheetScaffold`), a meia altura em repouso e recolhida ao peek enquanto um arrasto está em
  curso; em janela extra-larga é um detalhe pane-only no `DetailPane`. Um `ModalBottomSheet` é
  outra raiz de composição e o ponteiro não a atravessa — o próprio código paga esse preço com
  `exposeTestTags()` em cada sheet (`ModalManager.kt:159`).
- **Um motor de arrasto próprio, num módulo `core/dragdrop`.** A `reorderable` recebe um
  `LazyListState` e o arrasto nasce e morre dentro dele; não há "sair da lista". O motor novo tem
  um host global que desenha o ghost acima de tudo na mesma árvore (no desktop o painel está fora
  da subárvore da tela), fontes, alvos e autoscroll. A dependência `sh.calvin.reorderable` sai — o
  editor era o único uso.
- **As configurações do widget abrem numa modal do `ModalManager`.** Hoje são um `AdaptiveModal`
  no `DetailPaneController`; no desktop, isso disputaria o painel com a vitrine.
- **Apagado em modo edição significa "não aparece nesta largura de janela".** Um widget cujo
  `modes` não inclui o `WindowMode` atual aparece apagado, com legenda, na vitrine **e** na lista,
  e continua arrastável — o dashboard é um só, editado para todos os dispositivos.
- **O que se salva não muda.** `Preference(key, position, config)` continua sendo a persistência;
  um layout salvo antes desta mudança reabre idêntico.

## Capabilities

### New Capabilities

- `dashboard-widget-showcase`: o modo de edição do dashboard como duas superfícies — a lista do
  que está no dashboard e a vitrine do que pode entrar —, o arrasto como único verbo entre elas, a
  apresentação da vitrine por largura de janela (sheet inline / painel), o card como preview real
  e inerte, o apagado por modo de janela, e a persistência inalterada.
- `drag-and-drop`: o motor de arrasto do app — um host por janela que desenha o ghost acima de
  toda a árvore, fontes com gatilho imediato ou por toque longo, alvos que recebem entrada,
  movimento, saída e soltura por bounds em coordenadas de janela, o índice de slot como função
  pura, autoscroll de lista, e o cancelamento quando se solta fora de qualquer alvo.

### Modified Capabilities

- `adaptive-detail-pane`: "Apresentação adaptativa dos detalhes por largura de janela",
  "Mecanismo de detalhe distinto do gerenciador de modais transitórios" e "Detalhes pane-only
  distintos de detalhes sheet-capable" deixam de nomear as configurações do widget do dashboard
  como superfície adaptativa — elas passam a ser modal transitório via `ModalManager` — e a
  vitrine de widgets passa a ser o exemplo de detalhe pane-only.

## Impact

**Módulo novo — `:core:dragdrop`** (`finsight.compose.library`): `DragAndDropHost`,
`LocalDragAndDropState`, `Modifier.dragSource`, `Modifier.dropTarget`, autoscroll e a função pura
de índice de slot. Entra em `settings.gradle.kts`, na lista de módulos de `CLAUDE.md`, e como
dependência de `:app:shared` (monta o host) e de `feature/dashboard/impl`.

**`:app:shared`**: `App.kt` monta `DragAndDropHost` dentro de `DetailPaneHost`, acima de
`SharedTransitionProvider`/`ChromeHost` — o ghost precisa cobrir o painel lateral, que o
`ChromeHost` compõe como irmão do conteúdo (`ChromeHost.kt:273, 345`).

**`feature/dashboard/impl`**: `DashboardEditingContent` é reescrito (lista + sheet inline);
`DashboardEditLayout` troca `move(fromKey, toKey)` por `add/remove/move`; `DashboardUiState.Editing`
perde `availableItems` e ganha o catálogo; `DashboardAction` troca `MoveComponent(fromKey, toKey)`,
`AddAllComponents` e `RemoveAllComponents` por `AddComponent`, `RemoveComponent` e
`MoveComponent(from, to)`; entra `DashboardShowcasePane : AdaptivePane` e o conteúdo da vitrine
compartilhado pelas duas apresentações; `DashboardComponentOptionsModal` passa de `AdaptiveModal`
a `ModalBottomSheet`; `EditListEntry` perde as sentinelas; sai a dependência `reorderable`.

**Strings** (pt e en, no mesmo commit): saem `dashboard_edit_available_section`,
`dashboard_edit_active_placeholder`, `dashboard_edit_available_placeholder`,
`dashboard_edit_add_all`, `dashboard_edit_remove_all`; entram o título da vitrine, o texto da zona
de remoção, o vazio da vitrine e a legenda de "fora desta largura de janela".

**Persistência e banco**: nada. `DashboardComponentPreference`, o repositório, o builder e a
migração de widget deprecado ficam como estão.

**Specs**: `dashboard-widget-showcase` e `drag-and-drop` nascem; `adaptive-detail-pane` recebe
delta. `dashboard-balance-widgets` já fala em "vitrine de adição do modo de edição" e não muda.

**Testes**: `DashboardEditLayoutTest` (12 casos sobre sentinelas) é substituído por testes de
`add/remove/move` e da vitrine derivada; `DashboardDeprecatedWidgetTest` passa a ler a vitrine;
`core/dragdrop` ganha teste da função de slot e um teste de UI Compose (`compose.uiTest`, já usado
em `core/designsystem`) que arrasta entre dois alvos sob o host. O flow
`.maestro/flows/dashboard/customization.yaml` é reescrito — sete usos do cabeçalho e os dois
comandos em massa não têm tradução —, e o estado vazio passa a custar onze arrastos em vez de um
toque.

**Fora desta mudança, deliberadamente**: o mesmo widget mais de uma vez. Exige um `id` por
instância, e a chave do tipo hoje chega à persistência, às chaves da `LazyColumn`, às test tags, a
`UpdateComponentConfig` e ao builder (`DashboardComponent.kt:26`). O desenho daqui deixa o caminho
curto — adicionar o `id` e deixar de subtrair os ativos do catálogo —, e o levantamento está na
página de alinhamento (`vitrine-de-widgets.html`, nesta pasta).
