## Why

Ao navegar do cartão da dashboard para a tela de cartões, um cartão é desenhado por cima do `NavigationRail` durante toda a animação. O cartão que aparece sobre o rail **não é o que o usuário tocou**: é a página vizinha do `HorizontalPager`.

`CreditCardCard` aplica `Modifier.sharedElement` incondicionalmente sempre que encontra os scopes no ambiente (`core/ui/.../CreditCardCard.kt:62-72`). Como os dois pagers usam `contentPadding = 16.dp`, as páginas vizinhas ficam compostas (slivers de 8dp) e recebem o mesmo tratamento. Elas casam entre as duas telas, são elevadas ao overlay do `SharedTransitionLayout` e ali **perdem o clip do pager** — o default `ParentClip` devolve `null` quando não há shared element pai. A página anterior, posicionada em x ≈ −(viewport − 24dp), passa a ser pintada inteira, cobrindo a faixa do rail.

O defeito de fundo é de desenho: um componente de `:core:ui` decide sozinho participar de uma transição compartilhada. O chamador — o único que sabe qual cartão o usuário nomeou — não tem voz. Isso contraria a regra de derivação do projeto: o consumidor decide *se* uma regra se aplica, nunca *qual* ela é.

## What Changes

- **A transição compartilhada do cartão passa a ser opt-in do chamador.** `CreditCardCard` deixa de aplicar `Modifier.sharedElement` por conta própria; `:core:ui` passa a expor um `Modifier` nomeado que o chamador aplica ao cartão que ele decidiu promover. A chave (`credit_card_<id>`) continua com um único dono, em `:core:ui`. **BREAKING** para os três call sites de `CreditCardCard`.
- **Só o cartão selecionado participa.** Dashboard promove a página corrente do pager; a tela de cartões promove a página de `selectedCardIndex`. As páginas vizinhas deixam de ser elevadas ao overlay — e, com isso, deixam de escapar do clip.
- **O chrome da casca passa a ser desenhado acima do overlay.** `SharedTransitionProvider` sobe em `App.kt` para envolver o `ChromeHost`, e `NavigationRail`, bottom bar e FAB recebem `Modifier.renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f)`. Defesa estrutural: qualquer shared element futuro, com qualquer trajetória, fica abaixo do chrome.
- **Fallbacks documentados** em `design.md`, caso o sintoma persista após as duas medidas acima.

## Capabilities

### New Capabilities
- `shared-element-transitions`: quem decide que um elemento participa de uma transição compartilhada, quais elementos podem participar simultaneamente sob a mesma chave, e a garantia de que o chrome da casca nunca é encoberto pelo overlay de transição.

### Modified Capabilities
<!-- Nenhuma. Os requisitos de `navigation` e `adaptive-detail-pane` não mudam; a ordem de
     desenho entre chrome e conteúdo nunca foi especificada, e passa a ser em `shared-element-transitions`. -->

## Impact

- `core/ui/.../component/CreditCardCard.kt` — remove a decisão implícita; expõe o `Modifier` de promoção.
- `core/designsystem/.../component/SharedTransitionProvider.kt` — provider passa a ser usado acima da casca.
- `app/shared/.../ui/App.kt` — `SharedTransitionProvider` envolve `ChromeHost`; o `padding` sai de dentro do provider.
- `feature/shell/impl/.../screen/home/ChromeHost.kt` — rail, bottom bar e FAB no overlay do escopo.
- `feature/dashboard/impl/.../screen/dashboard/DashboardComponentContent.kt` — promove só a página corrente.
- `feature/creditcards/impl/.../screen/creditCards/CreditCardsScreen.kt` — promove só a página selecionada.
- `feature/report/impl/.../screen/report/config/ReportConfigScreen.kt` — call site ajustado; não promove nada (hoje já não participa, por não ter `AnimatedVisibilityScope`).
- Sem impacto em domínio, ledger, banco ou DI.
