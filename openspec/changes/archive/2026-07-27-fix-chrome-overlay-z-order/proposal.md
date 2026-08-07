## Why

No modo compacto, ao navegar entre a dashboard e qualquer outra tela, a barra de navegação inferior é desenhada **por cima** do FAB de adicionar transação durante toda a animação. O FAB é *docked* — `Modifier.offset(y = 40.dp)` (`ChromeHost.kt:165`) empurra cerca de 24dp do círculo para dentro da `NavigationBar`, que é uma superfície opaca com `tonalElevation = 8.dp` — então a sobreposição come um pedaço visível do botão.

A causa é um empate de prioridade no overlay de transição. O change anterior (`fix-credit-card-shared-transition-overlay`) elevou rail, barra e FAB ao overlay para que nenhum elemento compartilhado os encobrisse, e deu **o mesmo** `zIndexInOverlay = 1f` aos três (`ChromeHost.kt:227`). O overlay ordena com `renderers.sortBy { it.zIndex }` — um sort **estável** — de modo que z-index iguais são desempatados pela ordem de inserção, que segue a ordem de `subcompose` do `Scaffold`: `Fab` **antes** de `BottomBar`. Só que a ordem de *placement* do `Scaffold`, que vale fora do overlay, é a oposta: `bottomBarPlaceable.place(...)` antes de `fabPlaceable.place(...)`.

Ou seja: fora da transição o FAB é pintado por cima (correto); dentro do overlay a ordem se inverte. O `1f` repetido nunca foi uma decisão de que barra e FAB têm a mesma prioridade — foi a repetição de uma constante que só queria dizer "acima dos elementos compartilhados".

Três condições precisam ser simultâneas para o defeito aparecer, o que explica por que ele é tão estreito:

1. **Janela compacta** — só nela o FAB é docked e intersecta a barra; no modo wide o FAB é `header` do rail, sem interseção.
2. **Existe pelo menos um cartão de crédito** — `creditCardSharedElement` é o único elemento compartilhado do app, e `renderInSharedTransitionScopeOverlay` só eleva conteúdo enquanto `isTransitionActive`. Sem cartão, nada ativa a transição, o chrome nunca sai do `Scaffold`, e a ordem de placement prevalece.
3. **A tela é a dashboard** — dos dois call sites de `creditCardSharedElement`, `CreditCardsRoute` não é `primaryTab` (`AppNavCatalog.kt:67`), então lá o chrome já é `ContentOnly`. A dashboard é a única interseção de "há um cartão na tela" com "o chrome está visível".

## What Changes

- **A ordem de pintura no overlay passa a ser explícita e nomeada.** Uma escala de três níveis substitui os `1f` repetidos e o `0f` implícito: elemento compartilhado abaixo de tudo, chrome de navegação no meio, FAB acima de tudo.
- **O FAB sobe acima da barra e do rail.** É a única mudança de comportamento: a ordem no overlay passa a espelhar a ordem de placement do `Scaffold`, e o FAB fica integralmente visível durante a transição, como já fica fora dela.
- **O nível do elemento compartilhado deixa de ser um default.** Hoje `creditCardSharedElement` fica em `0f` por ser o default do Compose. "O cartão fica abaixo de todos" passa a ser declarado no call site, não herdado.
- **A escala vive em `:core:designsystem`.** Os três níveis estão em módulos que não podem se nomear — o cartão em `:core:ui`, o chrome em `:feature:shell:impl`. `SharedTransitionProvider.kt` já é dono do vocabulário do overlay (`LocalSharedTransitionScope`, `LocalAnimatedVisibilityScope`); a escala se junta a ele, e os dois lados a consomem sem que nenhum nomeie o outro.

Sem mudança de layout, de animação, de API pública de feature ou de qualquer superfície fora do desenho.

## Capabilities

### New Capabilities
<!-- Nenhuma. -->

### Modified Capabilities
- `shared-element-transitions`: o requisito "O chrome da casca é desenhado acima do overlay de transição" hoje especifica apenas a relação *entre* chrome e elementos compartilhados, tratando rail, barra e FAB como um bloco de prioridade única. Passa a especificar também a ordem **dentro** do chrome, e a exigir que a pilha inteira seja declarada num único lugar.

## Impact

- `core/designsystem/.../component/SharedTransitionProvider.kt` — passa a declarar a escala de prioridades do overlay.
- `core/ui/.../component/CreditCardCard.kt` — `creditCardSharedElement` declara seu nível em vez de herdar o default.
- `feature/shell/impl/.../screen/home/ChromeHost.kt` — `aboveSharedElements` passa a receber o nível; barra e rail num, FAB noutro; KDoc atualizado.
- Sem impacto em domínio, ledger, banco, DI, navegação ou layout.
