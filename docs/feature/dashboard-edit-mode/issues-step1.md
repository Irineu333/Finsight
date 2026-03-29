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

## Observação sobre recorrência (Issue 1)

O problema do long press em componentes com ação é recorrente em implementações de IA porque a solução intuitiva (`pointerInput { detectTapGestures }`) funciona em componentes *sem* ação mas falha silenciosamente nos que têm. A distinção entre `PointerEventPass.Main` e `PointerEventPass.Initial` não é óbvia, e a causa do bug não produz nenhum erro visível — o gesto simplesmente é ignorado.
