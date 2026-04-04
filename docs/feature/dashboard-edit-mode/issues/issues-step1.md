# Dashboard Edit Mode — Step 1: Known Issues

## Issue 1 — Long press não aciona o modo edição em componentes com ação

**Severidade:** Crítica — resolvida

**Descrição:**
Componentes que possuem ação própria (ex.: tap para navegar, tap para abrir modal) não detectavam o gesto de long press para entrar no modo edição.

**Causa raiz:**
`detectTapGestures` usa `PointerEventPass.Main` por padrão. Os componentes filhos com `clickable`/`combinedClickable` também rodam no Main pass e competem com o detector externo — o filho vence por ser processado primeiro na cadeia de modifiers, engolindo o gesto antes de o threshold de long press ser atingido.

**Correção:**
Substituir `pointerInput { detectTapGestures(onLongPress = ...) }` por uma extensão `interceptLongPress` que usa `PointerEventPass.Initial`. O Initial pass intercepta eventos *antes* de qualquer filho na árvore de composables, garantindo que o detector externo sempre veja o gesto independentemente das ações internas do componente.

```kotlin
private fun Modifier.interceptLongPress(onLongPress: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
        var released = false
        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                if (!event.changes.any { it.pressed }) {
                    released = true
                    break
                }
            }
        }
        if (!released) onLongPress()
    }
}
```

---

## Issue 2 — Arrastar componente não funciona corretamente no modo edição

**Severidade:** Crítica — resolvida

**Descrição:**
No modo edição, o gesto de arrastar estava com comportamento bugado. Não era possível mover livremente o item por todas as posições da lista de forma fluida.

**Causa raiz:**
`Crossfade(targetState = uiState)` usa identidade de objeto para comparação. A cada `MoveComponent`, o ViewModel gera um novo objeto `DashboardUiState.Editing` com os itens reordenados — objeto diferente do anterior. Isso faz o `Crossfade` acreditar que precisa animar para um novo estado, destruindo e recriando o composable filho (incluindo `lazyListState` e `reorderState`) no meio do drag. O drag perde sua âncora de estado e quebra.

**Correção:**
Usar `Transition<T>.Crossfade(contentKey = { it::class })` em vez do `Crossfade` standalone. A API de extensão em `Transition` expõe o parâmetro `contentKey`, que faz a animação disparar apenas quando o *tipo* do estado muda (`Loading → Viewing → Editing`), não quando o *conteúdo* muda dentro do mesmo tipo. Reordenações dentro do `Editing` não destroem mais o composable.

```kotlin
// Antes — quebra o drag
Crossfade(targetState = uiState) { state -> ... }

// Depois — anima só na troca de modo
updateTransition(targetState = uiState).Crossfade(
    contentKey = { it::class },
) { state -> ... }
```

---

## Issue 3 — Modo edição exibe apenas componentes com dados

**Severidade:** Funcional — resolvida

**Descrição:**
Ao entrar no modo edição, apenas os componentes que possuíam dados eram exibidos na lista de itens. Componentes sem dados (ex.: `CreditCardsPager` sem cartões cadastrados, `SpendingPager` sem gastos) apareciam em `availableItems` como se tivessem sido removidos pelo usuário — tornando impossível reordenar um componente que ainda não tinha conteúdo.

**Causa raiz:**
`buildEditingState()` construía `items` iterando `viewing.components` — a lista de componentes já filtrada pelo `DashboardComponentsBuilder`, que omite componentes sem dados no modo visualização. A distinção entre "componente removido pelo usuário" e "componente sem dados" não existia: ambos iam parar em `availableItems`.

**Correção:**
Dissociar a fonte de verdade do modo edição do estado filtrado do modo visualização:

- **Sem preferências salvas** → `items` = todos os entries do `DashboardComponentRegistry` na ordem padrão; `availableItems` = vazio
- **Com preferências salvas** → `items` = o que está nas preferências (na ordem salva); `availableItems` = entries do registry que não estão nas preferências (explicitamente removidos pelo usuário)

A filtragem por dados (`DashboardComponentsBuilder`) continua aplicada exclusivamente no modo visualização.

```kotlin
// Antes — usa a lista já filtrada por dados
val items = viewing.components.mapNotNull { component -> ... }
val availableItems = DashboardComponentRegistry.entries.filter { it.key !in presentKeys }

// Depois — usa registry/preferências, independente de dados
if (savedPrefs.isEmpty()) {
    items = DashboardComponentRegistry.entries.map { entry -> DashboardEditItem(...) }
    availableItems = emptyList()
} else {
    items = savedPrefs.sortedBy { it.position }.mapNotNull { pref -> ... }
    availableItems = DashboardComponentRegistry.entries.filter { it.key !in presentKeys }
}
```

---

## Observação sobre recorrência (Issue 1)

O problema do long press em componentes com ação é recorrente em implementações de IA porque a solução intuitiva (`pointerInput { detectTapGestures }`) funciona em componentes *sem* ação mas falha silenciosamente nos que têm. A distinção entre `PointerEventPass.Main` e `PointerEventPass.Initial` não é óbvia, e a causa do bug não produz nenhum erro visível — o gesto simplesmente é ignorado.

---

## Issue 4 — Scroll da dashboard aciona o modo edição

**Severidade:** Crítica — resolvida

**Descrição:**
Ao rolar a dashboard verticalmente, o modo edição podia ser acionado mesmo sem o usuário manter o dedo parado em um componente. O comportamento esperado é: `scroll` apenas rola a lista; modo edição só entra com `long press` sem deslocamento de scroll.

**Causa raiz:**
O `interceptLongPress` introduzido na correção do Issue 1 aguardava apenas o timeout de long press e verificava se o dedo ainda estava pressionado. Como o detector rodava no `PointerEventPass.Initial`, ele continuava vendo os eventos antes do `LazyColumn` consumir o gesto de scroll. Na prática, bastava manter o dedo pressionado enquanto iniciava a rolagem para o timeout expirar e o callback de `EnterEditMode` disparar, porque o detector não cancelava ao exceder o `touchSlop` nem ao perceber que o movimento já tinha sido consumido pelo scroll.

**Correção:**
Manter o detector no `PointerEventPass.Initial`, mas adicionar critérios explícitos de cancelamento do long press:

- Cancelar se a distância entre a posição atual e o `down` inicial ultrapassar `viewConfiguration.touchSlop`
- Cancelar se, no `PointerEventPass.Final`, o `PointerInputChange` tiver sido consumido pelo scroll
- Disparar `onLongPress()` apenas quando o toque completar o timeout sem release e sem cancelamento

```kotlin
private fun Modifier.interceptLongPress(onLongPress: () -> Unit): Modifier = pointerInput(onLongPress) {
    awaitEachGesture {
        val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
        var released = false
        var canceled = false

        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: run {
                    canceled = true
                    break
                }

                if (!change.pressed) {
                    released = true
                    break
                }

                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    canceled = true
                    break
                }

                val finalEvent = awaitPointerEvent(pass = PointerEventPass.Final)
                val finalChange = finalEvent.changes.firstOrNull { it.id == down.id } ?: run {
                    canceled = true
                    break
                }

                if (finalChange.isConsumed) {
                    canceled = true
                    break
                }
            }
        }

        if (!released && !canceled) onLongPress()
    }
}
```

**Resultado esperado após a correção:**

- `Long press` com dedo parado em qualquer componente continua entrando em edit mode
- `Scroll` vertical da dashboard não aciona edit mode
- Componentes com `clickable` interno continuam permitindo a interceptação do long press externo

---

## Issue 5 — Long press curto aciona edit mode e também a ação do componente

**Severidade:** Crítica — resolvida

**Descrição:**
Em componentes com ação própria na dashboard, um `long press` curto podia disparar dois efeitos no mesmo gesto:

- entrar no modo edição
- executar a ação original do componente no `pointer up`

Exemplo real: pressionar por tempo suficiente no card de cartões fazia a dashboard entrar em modo edição e, na soltura do dedo, também abrir a tela de cartões.

**Causa raiz:**
O `interceptLongPress` já reconhecia corretamente o gesto no `PointerEventPass.Initial` e disparava `EnterEditMode`, mas encerrava o detector logo em seguida. Como os eventos restantes do gesto não eram consumidos, o `clickable`/`Card(onClick)` interno ainda recebia o `up` e concluía o `tap`.

Na prática, o modo edição “ganhava” o threshold do long press, mas não cancelava explicitamente a ação de tap do componente filho.

**Correção:**
Após disparar `onLongPress()`, continuar observando o mesmo ponteiro e consumir todos os eventos até o dedo ser solto. Isso espelha o comportamento do `clickable` nativo do Compose: quando o long press vence, o restante do gesto deixa de estar disponível para a ação de click.

```kotlin
private fun Modifier.interceptLongPress(onLongPress: () -> Unit): Modifier = pointerInput(onLongPress) {
    awaitEachGesture {
        val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
        var released = false
        var canceled = false

        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: run {
                    canceled = true
                    break
                }

                if (!change.pressed) {
                    released = true
                    break
                }

                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    canceled = true
                    break
                }

                val finalEvent = awaitPointerEvent(pass = PointerEventPass.Final)
                val finalChange = finalEvent.changes.firstOrNull { it.id == down.id } ?: run {
                    canceled = true
                    break
                }

                if (finalChange.isConsumed) {
                    canceled = true
                    break
                }
            }
        }

        if (released || canceled) return@awaitEachGesture

        onLongPress()

        while (true) {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
        }
    }
}
```

**Resultado esperado após a correção:**

- `Tap` curto continua executando apenas a ação do componente
- `Long press` continua entrando no modo edição
- O mesmo gesto nunca dispara edit mode e ação de navegação/modal ao mesmo tempo
