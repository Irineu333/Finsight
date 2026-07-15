## Context

O app já tem um painel de detalhe adaptativo (`adaptive-detail-pane`): em janela **extra-larga** o `ChromeHost` reserva um `DetailPane` de 400dp à direita, e features abrem detalhes via `LocalDetailPaneController.show(AdaptiveModal(id))`. O `DetailPaneHost` também monta um `DetailSheetHost` que, abaixo de extra-larga, rebaixa o detalhe corrente a `ModalBottomSheet`. O molde do `AdaptiveModal` foi desenhado para detalhes `view*`: o `DetailPane` embrulha `RenderBody()` num `verticalScroll` top-aligned com `heightIn(min = viewport)` e ancora `RenderActions()` num rodapé fixo com sombra.

O Support hoje é 100% navegação: `SupportListRoute` (`SupportScreen`) → `navigate(SupportIssueRoute(id))` → `SupportIssueScreen` (Scaffold próprio: topbar, `LazyColumn` de mensagens com `animateScrollToItem(último)`, e bottomBar com `ReplyComposer`). Como o NavHost inteiro renderiza dentro do `content` do `ChromeHost`, em janela extra-larga a lista de suporte já convive com um painel reservado **vazio** à direita.

A conversa não encaixa no molde `view*`: ela é dona do próprio scroll (auto-scroll para o fim, `LazyColumn`) e o composer é um input, não "ações". Além disso, o usuário quer que em janela estreita o chat permaneça **tela cheia por navegação** (para preservar a transição do NavHost), não bottom sheet.

## Goals / Non-Goals

**Goals:**
- Abrir o chat de uma issue no painel de detalhe em janela extra-larga, reaproveitando a casca do painel existente.
- Manter o chat como tela cheia por navegação (com a transição do NavHost) quando a janela não é extra-larga.
- Não duplicar a UI da conversa entre rota e painel (DRY).
- Decisão de apresentação simples e pontual, sem máquina de sincronização entre apresentações.

**Non-Goals:**
- Fonte única de verdade para "issue selecionada".
- Preservação de instância/estado (ViewModel, rascunho, scroll) ao cruzar o breakpoint.
- Transformação instantânea painel↔tela ao redimensionar (é aceito fechar/permanecer conforme a origem).
- Deep-link direto para uma issue específica; mudanças no repositório/Firebase.

## Decisions

### 1. `ChatDetail` pane-only, distinto do `AdaptiveModal` sheet-capable
Introduzir a distinção **sheet-capable** vs **pane-only** no mecanismo do painel. Os `view*` de hoje continuam sheet-capable (painel em largo, bottom sheet em estreito). O chat é um detalhe **pane-only** que:
- renderiza **full-bleed** (dono do layout: header + `LazyColumn` com auto-scroll + composer fixo), sem o wrapper de `verticalScroll`/`heightIn` nem o slot de `RenderActions`;
- é exibido **exclusivamente** no painel; ao sair de extra-larga, é **dispensado** pelo host, não rebaixado a sheet.

A distinção é uma propriedade do próprio detalhe (ex.: `AdaptiveModal` sheet-capable + um `AdaptiveDetail` pane-only, ou um flag/hierarquia selada), para que o `DetailPaneHost`/`DetailSheetHost` escolham a apresentação sem conhecer a feature. O `DetailSheetHost` passa a ignorar (e o host a dispensar) detalhes pane-only quando a janela não é extra-larga.

**Alternativas descartadas:** (a) flag `ownsScroll` no `AdaptiveModal` — resolveria só o scroll, deixando o narrow e o composer destoando; (b) hospedar as duas apresentações no controller (instância única) — preservaria estado de graça, mas mataria a transição do NavHost no narrow.

### 2. Decisão painel-vs-navegação no clique
`SupportScreen` decide no `onOpenIssue`: `if (isExtraWideWindow()) controller.show(ChatDetail(id)) else navController.navigate(SupportIssueRoute(id))`. `SupportScreen` já tem `LocalDetailPaneController` e consegue ler a largura. Sem `LaunchedEffect(largura)` de sync; a bifurcação mora na feature, não no shell.

### 3. `ChatContent` compartilhado (DRY)
Extrair da atual `SupportIssueScreen` um composable `ChatContent` (cabeçalho, lista de mensagens com divisores de dia e auto-scroll, `ReplyComposer`), parametrizado pelo `SupportIssueViewModel`. A `SupportIssueScreen` (rota) envolve o `ChatContent` num `Scaffold` (topbar com voltar + bottomBar). O `ChatDetail` (painel) monta o mesmo `ChatContent` full-bleed com o composer no rodapé do painel. O `ChatDetail`, como os `view*`, é `ViewModelStoreOwner` para hospedar o `SupportIssueViewModel(issueId)` enquanto está no painel — sem contrato de preservação entre hosts.

### 4. Política de resize
- Chat no **painel** que cruza para fora de extra-larga → o host **dispensa** o `ChatDetail` (fecha). Consequência aceita: rascunho e scroll perdidos.
- Chat na **rota** → permanece tela cheia em qualquer largura; em extra-larga, o painel reservado à direita mostra o empty-state (redundância aceita, só via resize).

## Risks / Trade-offs

- **Sem fonte única de verdade:** "issue aberta" existe ou como rota (backstack) ou como `controller.current`, nunca derivada de um estado comum. Aceito em prol da simplicidade.
- **Perda de estado no resize do painel:** fechar o `ChatDetail` ao encolher descarta rascunho/scroll. Mitigação parcial: o chat re-observa o Firestore ao reabrir; só o rascunho digitado se perde de fato.
- **Redundância visual pós-resize:** chat como rota em janela extra-larga aparece no centro com o painel vazio ao lado. Só ocorre via resize (raro); documentado na spec.
- **Vazamento do conceito pane-only no core:** o `DetailPaneHost`/`DetailSheetHost` ganham a distinção pane-only/sheet-capable. Mantido genérico (propriedade do detalhe), sem o core conhecer o Support.
