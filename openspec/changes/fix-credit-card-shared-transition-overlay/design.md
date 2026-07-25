## Context

O sintoma relatado — o cartão sobrepondo o `NavigationRail` durante a navegação dashboard → cartões — tem uma causa que não é visível pela geometria do cartão tocado. Vale registrar o diagnóstico, porque ele é o que separa a correção certa das quatro correções plausíveis e erradas.

**Geometria, janela de 1280dp, rail de 80dp → viewport do conteúdo = 1200dp:**

```
página = viewport − 2·16 = 1168dp        pageSpacing = 8dp

   x = −1160          x = 16            x = +1192
 ┌───────────┐     ┌───────────┐      ┌───────────┐
 │ página N-1│     │ página N  │      │ página N+1│
 └───────────┘     └───────────┘      └───────────┘
       ▲                 ▲
  clipada pelo      o cartão tocado — x e largura
  pager (sliver     idênticas nas duas telas
  de 8dp visível)   (16dp / 1168dp): nunca cruza x=0
```

**A cadeia, com evidência direta no código:**

1. `core/ui/.../CreditCardCard.kt:62-72` aplica `Modifier.sharedElement` em **toda** instância que encontre `LocalSharedTransitionScope` e `LocalAnimatedVisibilityScope` não nulos — incluindo as páginas vizinhas.
2. Os dois pagers (`DashboardComponentContent.kt:569-588`, `CreditCardsScreen.kt:432-449`) usam `contentPadding = 16.dp`, então os vizinhos **são compostos**. `beyondViewportPageCount` não é usado em lugar nenhum do repositório.
3. Os vizinhos casam entre origem e destino (mesmos `cardId`), logo `foundMatch = true` e eles são elevados ao overlay.
4. No overlay, o desenho é `translate(x, y) { drawLayer(layer) }` — `SharedElementEntry.kt:78-91`.
5. O clip default é `ParentClip`, que devolve `parentSharedContentState?.clipPathInOverlay` — `SharedTransitionScope.kt:1470-1480`. Sem shared element pai, isso é `null`: **clip nenhum**. O clip do `HorizontalPager` e o do `LazyColumn` não se aplicam.
6. `ChromeHost.kt:168-194`: o rail e o `Box(weight = 1f)` são irmãos num `Row`; o `Box` desenha **depois** e não clipa.

Logo, a página N−1 é pintada a partir de x = −1160 e cobre a faixa do rail (−80..0) enquanto a transição dura.

**Descartado com evidência** (investigação registrada): descasamento de largura/inset entre origem e destino (ambos a 16dp/1168dp); pager de destino abrindo na página 0 (`CreditCardsViewModel.kt:43-52` resolve o índice antes da primeira emissão de `Content`); transições customizadas do `NavHost` (nenhuma — defaults de fade da navigation-compose 2.9.2); `AnimatedVisibilityScope` errado (provido só nos `composable<>`); chave duplicada em `ReportConfigScreen` ou no `DetailPane`; e o rail animando durante a navegação.

**Predição falsificável:** em janela ≥840dp, a página N+1 (x = +1192) deve pintar sobre o `DetailPane` (`ChromeHost.kt:186-192`). Confirmar isso antes de implementar valida o diagnóstico inteiro.

**Versões:** Compose Multiplatform 1.10.1 (animation 1.10.5/1.10.3), navigation-compose 2.9.2, material3-adaptive 1.2.0.

## Goals / Non-Goals

**Goals:**
- Que apenas o cartão nomeado pela ação do usuário participe da transição compartilhada.
- Que a decisão de participar pertença ao chamador, e a chave permaneça com um único dono.
- Que o chrome da casca (rail, bottom bar, FAB) fique estruturalmente acima do overlay, independente da trajetória de qualquer shared element presente ou futuro.
- Deixar registradas as alternativas, para o caso de o sintoma sobreviver às duas medidas.

**Non-Goals:**
- Corrigir o `ChromeEffect` ausente da `CreditCardsScreen` (bug real, separado).
- Reconciliar `EXCLUDED_CARD_IDS` entre a lista da dashboard e a da tela de cartões.
- Introduzir transição compartilhada em telas que hoje não têm.
- Trocar `sharedElement` por `sharedBounds` ou ajustar curvas de animação.

## Decisions

### D1 — A promoção a shared element é opt-in do chamador

`CreditCardCard` deixa de consultar os `CompositionLocal`s. `:core:ui` passa a expor, ao lado do componente:

```kotlin
@Composable
fun Modifier.creditCardSharedElement(cardId: Long): Modifier
```

que devolve `Modifier` quando os scopes não existem, e o `sharedElement` com a chave `credit_card_$cardId` quando existem. O chamador aplica no cartão que decidiu promover.

**Por que essa forma e não um `Boolean` no componente:** um parâmetro `sharedElementEnabled` mantém a decisão *dentro* do componente e obriga a passar por ele toda promoção futura. O `Modifier` nomeado é reuso explícito com acoplamento baixo — o chamador compõe, `:core:ui` continua sendo o único lugar que conhece a chave, e nenhuma decisão fica escondida.

**Alternativa descartada — deixar o chamador montar o `sharedElement` na feature:** duplicaria a chave em três módulos. A chave é uma regra derivável da identidade do cartão; ela tem exatamente um dono.

### D2 — Cada tela promove uma única página: a corrente

Dashboard promove `page == pagerState.currentPage`; a tela de cartões promove `page == selectedIndex` (que já é o `selectedCardIndex` resolvido pelo ViewModel, `CreditCardsViewModel.kt:45-52`). Os vizinhos ficam com `Modifier` vazio e nunca chegam ao overlay.

Isso resolve a causa: com um único par casado, o que é elevado é exatamente o cartão cuja origem e destino estão ambos em x = 16dp — a trajetória nunca cruza x = 0.

**Consequência aceita:** se o usuário deslizar o pager de destino durante a transição, a promoção muda de página no meio do caminho. É um caso de borda de milissegundos e a degradação é um corte de animação, não um artefato visual sobre o chrome.

### D3 — O chrome da casca vive dentro do overlay, acima dos shared elements

`App.kt` passa a ser:

```kotlin
SharedTransitionProvider {
    ChromeHost { padding ->
        AppNavHost(modifier = Modifier.padding(padding))
    }
}
```

e no `ChromeHost` o rail, a bottom bar e o FAB recebem `Modifier.renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f)`.

**Por que manter isso mesmo com D1+D2 resolvendo a causa:** D1/D2 corrigem *este* caso; D3 torna a classe inteira de casos impossível. É a solução canônica da documentação oficial (a bottom bar do Jetsnack) e não depende de conhecer a trajetória de nada. Um shared element futuro, numa tela que ainda não existe, não pode encobrir o chrome.

**Nota de camadas:** `feature:shell:impl` já depende de `:core:designsystem`, então ler `LocalSharedTransitionScope` no `ChromeHost` não fere nenhuma regra de módulo. E o provider deixa de morar dentro de um slot de conteúdo do `Scaffold` — passa à raiz da composição, que é onde pertence.

**Efeito colateral desejado:** com o chrome dentro do overlay, rail e bottom bar podem usar `animateEnterExit()` e participar das transições em vez de piscar. Não faz parte desta mudança, mas passa a ser possível.

### D4 — Fallbacks, caso o sintoma sobreviva

Aplicar **na ordem**, e só se a verificação falhar. Cada um é mais barato e menos correto que o anterior:

| # | Medida | Onde | Custo do remédio |
|---|---|---|---|
| F1 | `clipInOverlayDuringTransition = OverlayClip(shapes.large)` no `creditCardSharedElement` | `core/ui` | Nenhum. Só clipa ao próprio shape — não resolve vazamento de container, mas elimina cantos quadrados no overlay |
| F2 | `Modifier.clipToBounds()` no `Box(weight = 1f)` | `ChromeHost.kt:192` | O cartão é **cortado** ao cruzar a borda em vez de passar por baixo do rail. Troca um artefato por outro, menos feio |
| F3 | `Modifier.zIndex(1f)` no rail dentro do `Row` | `ChromeHost.kt:170` | Funciona por ordem de desenho entre irmãos. Redundante com D3; só faz sentido se D3 for revertido |
| F4 | `OverlayClip` customizado, clipando ao retângulo do viewport do pager | `core/ui` | Acopla o cartão à geometria do container. Último recurso |

F1 é o único que vale aplicar mesmo sem o bug persistir; os outros três são remédios.

## Risks / Trade-offs

- **[O diagnóstico está errado e o sintoma persiste]** → A predição de D-Context (vazamento simétrico sobre o `DetailPane` em ≥840dp) é verificada **antes** de implementar. Se ela falhar, o diagnóstico cai e a causa precisa ser reinvestigada antes de mexer no código. D3 mitiga o sintoma de qualquer forma.
- **[D1 é breaking para três call sites]** → São três, todos no repositório, todos listados no `proposal.md`. O compilador acha os três.
- **[Subir o `SharedTransitionProvider` muda o escopo de todo o app]** → O `SharedTransitionLayout` passa a medir a janela inteira em vez da área de conteúdo. Nada hoje depende dos bounds dele exceto o overlay, e o overlay é justamente o que queremos maior. O `padding(paddingValues)` continua aplicado ao `AppNavHost`, então o layout do conteúdo não muda.
- **[`renderInSharedTransitionScopeOverlay` no chrome muda a ordem de desenho do `Scaffold`]** → O chrome já desenhava por cima do conteúdo em condições normais; a mudança só garante que continue assim durante uma transição. Verificar que o `DropdownMenu` do topo e os `ModalBottomSheet` continuam acima do chrome.
- **[Regressão silenciosa]** → A transição é visual e não tem cobertura de teste automatizada viável. A verificação é manual, em janela larga e estreita, e nos dois sentidos da navegação (ida e volta).
