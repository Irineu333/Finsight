## Context

O editor do dashboard é `DashboardEditingContent`: uma `LazyColumn` com um
`rememberReorderableLazyListState` da `sh.calvin.reorderable:3.0.0`, cujos itens são
`EditListEntry` — os componentes ativos, um `SectionHeader`, os componentes disponíveis e dois
placeholders para os vazios. Ativar e desativar é a mesma operação que reordenar: o callback da
biblioteca entrega `(from.key, to.key)`, e `DashboardEditLayout.move` concatena as duas listas e
decide pela chave-alvo — componente, cabeçalho ou placeholder — de que lado da fronteira
(`activeCount`) o item cai (`DashboardEditLayout.kt:22-63`). `DashboardEditLayoutTest` prova os
cinco ramos. Tocar num ativo abre `DashboardComponentOptionsModal`, um `AdaptiveModal` no
`LocalDetailPaneController` — sheet no celular, painel de 400dp em janela extra-larga
(`AdaptiveDetail.kt:132-137`). O editor pede `ChromeConfig.ContentOnly` (`DashboardScreen.kt:44`).

**Fatos verificados que este desenho não escolhe:**

- Um `ModalBottomSheet` do Material 3 é uma raiz de composição própria — `Dialog` no Android,
  `Popup` em desktop e iOS. O repositório já paga esse preço: cada sheet chama
  `Modifier.exposeTestTags()` porque "a sheet is its own composition root" (`ModalManager.kt:159`,
  `AdaptiveDetail.kt:194`). Eventos de ponteiro não atravessam essa fronteira.
- O `DetailPane` é composto pelo `ChromeHost` como irmão do conteúdo, dentro do mesmo `Row`
  (`ChromeHost.kt:273, 345`) — está na mesma árvore, mas **fora** da subárvore de qualquer tela.
- `AdaptivePane` já existe: "pane-only, shown only in the extra-wide pane and dismissed — never
  demoted to a sheet — when the window shrinks" (`AdaptiveDetail.kt:76-80`); o `DetailSheetHost`
  o dispensa abaixo do breakpoint (`AdaptiveDetail.kt:194-200`).
- A `reorderable` recebe um `LazyListState` e o arrasto vive dentro dele. É usada só no editor.
- `DashboardComponentVariant.*.Preview` e `DashboardPreviewFactory.createPreview(key, on)` já
  fabricam o widget com dados fictícios na moeda-base; a inércia no editor vem de um
  `Box.matchParentSize()` com `clickable` + handle por cima do widget
  (`DashboardEditingContent.kt:203-213`), não da variante.
- `DashboardComponentType` declara `modes: Set<WindowMode>` (só `QUICK_ACTIONS` restringe, a
  `COMPACT`) e `isDeprecated`; o KDoc já chama a vitrine de *showcase*
  (`DashboardComponentType.kt:10`). A tela de visualização filtra por `mode in it.modes`
  (`DashboardViewingContent.kt:43`).
- A chave de um componente é a do tipo, por declaração: "How the component is named in the saved
  layout — the type's own key" (`DashboardComponent.kt:26`). Persistência
  (`SerializablePreference(key, position, config)`), chaves da `LazyColumn`, test tags,
  `UpdateComponentConfig` e o builder andam por ela.
- `core/designsystem` já tem `compose.uiTest` em `commonTest` e `compose.desktop.currentOs` em
  `jvmTest` (`FloatingActionMenuTest`): existe harness de UI Compose no JVM.
- O `Json` do repositório tem `ignoreUnknownKeys = true` (`DashboardPreferencesRepository.kt:16`).
- `App.kt` empilha `ModalManagerHost { DetailPaneHost { SharedTransitionProvider { ChromeHost … } } }`
  (`App.kt:150-155`).
- O layout padrão tem onze widgets (`.maestro/README.md:512`), e o flow
  `dashboard/customization` chega ao estado vazio por `dashboard_edit_remove_all`.

**Referência visual.** `vitrine-de-widgets.html`, nesta pasta, é a página de alinhamento com os
mockups do celular (repouso → levantou → soltou) e do desktop, a tabela de decisões e o registro
do que fica para a mudança das instâncias. É a mesma página publicada em
<https://claude.ai/code/artifact/3f486ccc-14e1-4edd-8b06-f7b6b3879c51>. Onde ela e este
documento divergirem, este documento vale.

## Goals / Non-Goals

**Goals:**

- O usuário decide **o que** está no dashboard e **em que ordem** em superfícies distintas, e move
  tudo com um gesto só: arrastar.
- A mesma experiência no celular e no desktop, com a vitrine onde cada largura a acomoda melhor —
  sheet inline ou painel lateral — sem que o gesto mude.
- Um motor de arrasto do app, reutilizável, que não conhece o dashboard.
- Nada muda no que se salva: um layout anterior reabre idêntico.

**Non-Goals:**

- O mesmo widget mais de uma vez (identidade por instância). Registrado em Impact da proposta e
  no fim da página de alinhamento; é a mudança seguinte.
- Comandos em massa ("Adicionar todos", "Remover todos"): saem e não voltam noutra forma.
- Trocar o `DetailPane` ou o `ModalManager` — só o que a vitrine e as opções precisam deles.
- Arrastar para fora do app ou entre janelas (drag and drop do sistema operacional).

## Decisions

### D1 — A vitrine nunca é modal: sheet inline abaixo do breakpoint, `AdaptivePane` acima

O ponteiro não atravessa uma raiz de composição, então a vitrine é composta na mesma árvore da
lista nas duas larguras. Em janela extra-larga é `DashboardShowcasePane : AdaptivePane`, aberta via
`DetailPaneController.show()` num efeito da tela keyed em `uiState is Editing && isExtraWideWindow()`
e dispensada quando qualquer um dos dois deixa de valer; quando a janela encolhe, o próprio
`DetailSheetHost` já a dispensa (`AdaptiveDetail.kt:194-200`) e a tela passa a compor a sheet.
Abaixo do breakpoint é um `BottomSheetScaffold` do Material 3 dentro de `DashboardEditingContent`
— o `BottomSheetScaffold` compõe a sheet no mesmo `Layout` que o corpo, sem `Popup`.

O conteúdo da vitrine é um composable só, `DashboardShowcaseContent`, hospedado pelas duas
apresentações; o breakpoint é `isExtraWideWindow()`, o mesmo do painel.

*Alternativa descartada:* `ModalBottomSheet` via `ModalManager`, como toda outra sheet do app —
mataria o arrasto entre a vitrine e a lista, que é o objetivo. *Alternativa descartada:* uma faixa
horizontal fixa no rodapé com cards reduzidos — reduzir escala esconde o que o preview existe para
mostrar, e a leitura vertical em largura total é a que a seção "Disponíveis" já dava.

### D2 — As três posições da sheet são um `sheetPeekHeight` dinâmico

O `BottomSheetScaffold` tem dois valores visíveis, `PartiallyExpanded` (a altura do peek) e
`Expanded`; não tem "meia altura". A meia altura em repouso e o recuo durante o arrasto são o
**mesmo estado** com `sheetPeekHeight` diferente: metade da altura disponível em repouso, a altura
da alça e do rótulo "Solte aqui para remover" enquanto `LocalDragAndDropState.current.session != null`,
animado com `animateDpAsState`. `Expanded` continua disponível para quem quer só olhar a vitrine;
se a sheet estava expandida quando o arrasto começou, o editor chama `partialExpand()` ao começar
e `expand()` ao terminar, para que ela volte onde estava.

*Alternativa:* uma sheet própria com `AnchoredDraggableState` e três âncoras. Fica como plano B
(ver Riscos) se a mudança de âncora do `BottomSheetScaffold` não animar de forma aceitável — custa
~60 linhas e perde o nested scroll e a acessibilidade que o Material dá de graça.

### D3 — Um motor próprio em `:core:dragdrop`, host global no `App`

A `reorderable` não sai de uma lista, e dois sistemas de gesto no mesmo item disputam o mesmo
`pointerInput` — o item já empilha `longPressDraggableHandle` e `draggableHandle`. Um dono para os
três gestos. O `dragAndDropSource/Target` do foundation é a sessão de arrasto do SO — sombra do
sistema, pensada para cruzar apps, sem slot animado nem ghost estilizado; o suporte iOS na CMP
1.10.1 não foi confirmado, e não mudaria a escolha.

O módulo é `:core:dragdrop`, sob `finsight.compose.library`, sem dependência de feature. Fica em
`core` e não em `feature` porque o motor não tem rota, tela, domínio nem `Entry`; porque uma
feature `api` não pode depender de outra `api` (`api ⊄ api`), o que impediria `core/ui` ou outro
`api` de oferecer arrasto; e porque é a prateleira dos seus irmãos, `ModalManager` e `DetailPane`.
Separado de `designsystem` porque tem tamanho e testes próprios e não puxa o tema.

A superfície:

```
DragAndDropHost(content)                       // monta uma vez; desenha o ghost num Box acima de content
LocalDragAndDropState: DragAndDropState        // session: DragSession? (payload, pointer em janela, ghost)
Modifier.dragSource(payload, trigger, ghost, onDragEnd)   // trigger = Immediate | LongPress
Modifier.dropTarget(priority, onEnter, onMove(offsetInTarget), onExit, onDrop(payload, offset))
fun slotIndexFor(pointerAlongAxis, visible: List<ItemBounds>): Int   // pura; testada
suspend fun LazyListState.autoscrollWhile(...)          // faixas de borda, velocidade por proximidade
```

O host guarda os alvos registrados (`LayoutCoordinates` → bounds em janela via
`onGloballyPositioned`) e resolve, a cada movimento, o alvo sob o ponteiro por prioridade e ordem
de registro. O ghost é o composable que a fonte forneceu, desenhado no host na posição do ponteiro
menos o offset em que o item foi agarrado, com `graphicsLayer` para sombra e leve rotação.

O host entra em `App.kt` dentro de `DetailPaneHost`, envolvendo `SharedTransitionProvider`: precisa
cobrir o painel, que o `ChromeHost` compõe, e não precisa cobrir as modais do `ModalManager`, que
são janelas próprias. O módulo vira dependência de `:app:shared` e de `feature/dashboard/impl`.

### D4 — O slot é um item real da lista; a `LazyColumn` anima os vizinhos

Durante um arrasto sobre a lista, o editor mantém `hoverIndex` (estado da composição, não do
ViewModel), calculado por `slotIndexFor(pointerY, lazyListState.layoutInfo.visibleItemsInfo)`.
A lista renderiza um `EditListEntry.Slot` nesse índice — vindo da vitrine, é um item extra com a
altura do ghost; vindo da própria lista, é o item arrastado apresentado como slot, na sua chave.
`Modifier.animateItem()` desloca os vizinhos; nada é transladado à mão. Na soltura, o editor
despacha uma ação só: `AddComponent(type, at = hoverIndex)` ou `MoveComponent(from, to)`. A vitrine
como alvo é trivial: qualquer soltura é `RemoveComponent(key)`.

Haptics nos três momentos que o editor já usa: `GestureThresholdActivate` ao levantar,
`SegmentFrequentTick` quando o slot muda de índice, `GestureEnd` ao soltar.

### D5 — O modelo do editor: lista ativa, catálogo, vitrine derivada

`DashboardUiState.Editing` passa a `(yearMonth, activeItems, catalog, accounts, creditCards)`,
onde `catalog` são os previews de todos os tipos não deprecados, construídos uma vez ao entrar
(são `suspend`). A vitrine é `catalog.filterNot { it.key in activeKeys }`, derivada na UI.
`DashboardEditLayout(activeItems)` ganha `add(item, at)`, que recusa um tipo já ativo e clampa o
índice, `remove(key)` e `move(from, to)`, e perde as sentinelas. `DashboardAction` perde
`MoveComponent(fromKey, toKey)`, `AddAllComponents` e `RemoveAllComponents`; ganha
`AddComponent(key, at)`, `RemoveComponent(key)`, `MoveComponent(fromIndex, toIndex)`.
`UpdateComponentConfig(key, config)`, `ConfirmEdit`, `CancelEdit` e a persistência não mudam.

Por que a vitrine é derivada e não estado: "disponível" é a ausência de "ativo" — guardar os dois
seria a segunda fonte de uma verdade só, e a regra de derivação do `CLAUDE.md` põe o dono no
lugar único. É também o que deixa a mudança das instâncias curta: ela para de subtrair.

### D6 — As configurações do widget viram `ModalBottomSheet` do `ModalManager`

`DashboardComponentOptionsModal` deixa de estender `AdaptiveModal` e passa a estender
`ModalBottomSheet`, aberta por `LocalModalManager.current.show(...)`. No desktop o painel é da
vitrine; uma modal transitória por cima do editor é o que a spec `adaptive-detail-pane` já prevê
para "formulário empilhado sobre um detalhe". O estado `config` continua na instância da modal; o
rodapé de ações passa a ser composto pela própria modal, já que o `ModalBottomSheet` não separa
corpo de ações. Delta em `adaptive-detail-pane`, três requisitos.

### D7 — Apagado em modo edição é "fora desta largura de janela", nos dois lugares

`DashboardEditItem` (lista) e o card da vitrine leem `variant.modes` contra o `WindowMode` atual e,
fora dele, aplicam alpha reduzido e uma legenda de uma linha — a mesma string nos dois lugares,
neutra quanto a plataforma ("Não aparece nesta largura de janela"), porque `WindowMode` é largura,
não dispositivo. Continuam arrastáveis. Um ativo nessa condição não some da lista: a tela de
visualização já o esconde (`DashboardViewingContent.kt:43`); o editor é onde ele é visto.

### D8 — Alça imediata em todo card; corpo por toque longo

Todo card — vitrine e lista — tem o `≡` com `DragTrigger.Immediate` e o overlay do corpo com
`DragTrigger.LongPress`. É o que o editor já faz e o que o flow Maestro depende: "the handle drags
on touch, unlike the item body" (`customization.yaml`). Com mouse, o toque longo aceita press+move.

### D9 — Test tags e o flow Maestro

Lista: `dashboard_edit_list`, `dashboard_edit_item_<key>`, `dashboard_edit_drag_<key>`. Vitrine:
`dashboard_showcase` (raiz da sheet e do painel), `dashboard_showcase_item_<key>`,
`dashboard_showcase_drag_<key>`, `dashboard_showcase_drop` (a zona do peek). A raiz da vitrine no
painel está sob o `Surface` com `exposeTestTags()` do `App` (`App.kt:146`); a sheet inline também
— por isso a vitrine não precisa de `exposeTestTags()` próprio, ao contrário das modais.

`customization.yaml` é reescrito: "está no dashboard" passa a ser "existe
`dashboard_edit_item_<key>`", "não está" passa a ser "existe `dashboard_showcase_item_<key>`";
adicionar é `swipe from: dashboard_showcase_drag_<key>, direction: UP`; remover é `swipe from:
dashboard_edit_drag_<key>, direction: DOWN` até o peek. O estado vazio custa onze remoções; o flow
paga, porque "o estado vazio se apresenta no seu próprio conteúdo e oferece o editor como única
saída" é uma afirmação que só o E2E prova.

### D10 — O que não muda, por decisão

`DashboardComponentPreference`, o repositório, `BuildDashboardViewingUseCase`,
`GetDashboardPreferencesUseCase` (o layout padrão), `DashboardArchiveReplacedHook`, a regra de
widget deprecado e `DashboardEditTip`. A entrada no editor continua por toque longo num widget ou
pelo botão do estado vazio.

## Risks / Trade-offs

- **[A mudança de `sheetPeekHeight` não anima bem no `BottomSheetScaffold`]** → medir na primeira
  tarefa da sheet; se saltar, trocar por uma sheet própria com `AnchoredDraggableState` e três
  âncoras (D2, plano B), mantendo a mesma API para o resto do editor.
- **[Autoscroll e zona de remoção disputam a borda inferior no celular]** → o recuo ao peek resolve
  por construção: a faixa de autoscroll é a base da lista, acima do peek; dentro do peek não há
  lista. A faixa é definida em dp (48) e testada na função de índice.
- **[Perder o que a `reorderable` dava de graça — autoscroll e deslocamento]** → o deslocamento é
  da `LazyColumn` (`animateItem`), não nosso; o autoscroll é uma corrotina de ~40 linhas com
  teste. É a parte chata, não a difícil.
- **[O ghost sob o `SharedTransitionProvider`]** → o host fica **acima** dele; nenhuma transição
  compartilhada roda com o editor aberto (`ContentOnly`), e o overlay de transição é inerte
  fora dela.
- **[`ChromeConfig.ContentOnly` e o painel]** → o `DetailPane` é composto independentemente do
  chrome (`ChromeHost.kt:344`); confirmado por leitura, verificado na tarefa do painel.
- **[O flow Maestro fica mais longo (onze remoções)]** → ~25 s a mais num flow de 1m44; aceito
  em D9. Se ultrapassar o orçamento do README, o estado vazio passa a ser coberto a partir de um
  layout menor, nunca deixado sem cobertura.
- **[Regressão de a11y na sheet inline]** → o `BottomSheetScaffold` mantém a semântica de
  expandir/recolher do Material; a vitrine no painel é conteúdo comum.
- **[Uma preferência salva com um tipo que a vitrine não oferece]** → não é caso novo: o widget
  deprecado já é "construído, previewado e renderizado" fora da vitrine; a lista o mostra e a
  vitrine não, exatamente como hoje.

## Migration Plan

Não há migração de dados: a persistência é a mesma. A ordem de entrega está em `tasks.md` —
o módulo e o modelo entram sem alterar comportamento visível, o editor novo substitui o antigo
num commit só (a tela não tem meio-termo entre uma lista e duas superfícies), e o flow Maestro
é reescrito no mesmo commit que muda as tags. Rollback é reverter esse commit; nada gravado em
disco o impede.

## Open Questions

Nenhuma. As três que restavam — onde mora o motor, o destino dos comandos em massa e o que a
vitrine faz com um tipo já ativo — foram fechadas na página de alinhamento (rev. 3).
