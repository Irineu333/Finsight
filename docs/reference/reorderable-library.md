# Referência: `sh.calvin.reorderable`

Biblioteca para drag-and-drop em listas Compose Multiplatform.

**Versão:** `3.0.0`
**Plataformas:** Android, iOS, Desktop (JVM), Web (wasmJs, js)

```kotlin
// composeApp/build.gradle.kts
implementation("sh.calvin.reorderable:reorderable:3.0.0")
```

---

## Imports

```kotlin
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.ReorderableCollectionItemScope  // para passar o scope a composables filhos
```

---

## API Core — LazyColumn

### 1. Criar estado

```kotlin
val lazyListState = rememberLazyListState()

val reorderState = rememberReorderableLazyListState(
    lazyListState = lazyListState,
    scrollThresholdPadding = WindowInsets.systemBars.asPaddingValues(), // para edge-to-edge
) { from, to ->
    // DEVE atualizar a lista SINCRONAMENTE antes de retornar.
    // Atualização após suspensão causa flickering visual do item.
    list = list.toMutableList().apply {
        add(to.index, removeAt(from.index))
    }
    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
}
```

### 2. Aplicar ao LazyColumn

```kotlin
LazyColumn(state = lazyListState) {
    items(list, key = { it.key }) { item ->
        ReorderableItem(reorderState, key = item.key) { isDragging ->
            // conteúdo do item
        }
    }
}
```

**A `key` passada para `ReorderableItem` deve ser idêntica à `key` usada em `items()`.**

### 3. Assinatura completa de `rememberReorderableLazyListState`

```kotlin
fun rememberReorderableLazyListState(
    lazyListState: LazyListState,
    scrollThresholdPadding: PaddingValues = PaddingValues(0.dp),
    scrollThreshold: Dp = 48.dp,  // ReorderableLazyCollectionDefaults.ScrollThreshold
    scroller: Scroller = rememberScroller(lazyListState),
    onMove: suspend CoroutineScope.(
        from: LazyCollectionItemInfo<LazyListItemInfo>,
        to: LazyCollectionItemInfo<LazyListItemInfo>
    ) -> Unit,
): ReorderableLazyListState
```

### 4. Assinatura de `ReorderableItem`

```kotlin
@Composable
fun LazyItemScope.ReorderableItem(
    state: ReorderableLazyListState,
    key: Any,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    animateItemModifier: Modifier = Modifier.animateItem(), // NÃO sobrescrever com Modifier — quebra animação dos vizinhos
    content: @Composable ReorderableCollectionItemScope.(isDragging: Boolean) -> Unit,
)
```

---

## Modifiers de Drag (somente dentro de `ReorderableCollectionItemScope`)

```kotlin
// Drag inicia IMEDIATAMENTE ao toque — BLOQUEIA cliques normais
Modifier.draggableHandle(
    enabled: Boolean = true,
    onDragStarted: (suspend CoroutineScope.(startedPosition: Offset) -> Unit)? = null,
    onDragStopped: (suspend CoroutineScope.(velocity: Float) -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
)

// Drag inicia após LONG PRESS — preserva cliques normais no elemento
Modifier.longPressDraggableHandle(
    enabled: Boolean = true,
    onDragStarted: (suspend CoroutineScope.(startedPosition: Offset) -> Unit)? = null,
    onDragStopped: (suspend CoroutineScope.(velocity: Float) -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
)
```

**Regra de escolha:**

| Cenário | Modifier correto |
|---------|-----------------|
| Ícone de drag handle dedicado (sem tap próprio) | `draggableHandle()` |
| Componente inteiro arrastável + também tappable (nosso caso) | `longPressDraggableHandle()` |
| Componente inteiro arrastável, sem interação de tap | `draggableHandle()` |

---

## Exemplo: componente inteiro arrastável + tappable

**Este é o padrão desta feature.** O item responde a tap (abre modal de opções) e a long press + drag (reordena).

```kotlin
val haptic = LocalHapticFeedback.current

LazyColumn(state = lazyListState) {
    items(state.items, key = { it.key }) { item ->
        ReorderableItem(reorderState, key = item.key) { isDragging ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // tap → abre modal de opções
                    .clickable { onTap(item) }
                    // long press + drag → reordena (longPressDraggableHandle preserva o clickable)
                    .longPressDraggableHandle(
                        onDragStarted = {
                            haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        },
                        onDragStopped = {
                            haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                        },
                    )
                    .shadow(if (isDragging) 8.dp else 0.dp),
            ) {
                // conteúdo do item
            }
        }
    }
}
```

---

## Exemplo: ícone de drag handle com `interactionSource` compartilhado

Quando o handle compartilha `interactionSource` com um Card clicável, o ripple do Card é acionado durante o drag:

```kotlin
ReorderableItem(reorderState, key = item.key) { isDragging ->
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        onClick = { onTap(item) },
        interactionSource = interactionSource,
    ) {
        Row {
            Text(item.title)
            IconButton(
                modifier = Modifier.draggableHandle(
                    interactionSource = interactionSource,
                    onDragStarted = {
                        haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    },
                ),
                onClick = {},
            ) {
                Icon(Icons.Rounded.DragHandle, contentDescription = null)
            }
        }
    }
}
```

---

## Passando o scope para composables filhos

Os modifiers de drag são extension functions em `ReorderableCollectionItemScope`. Ao extrair o conteúdo do item para uma função separada, passe `this`:

```kotlin
@Composable
fun DashboardEditItemWrapper(
    item: DashboardEditItem,
    isDragging: Boolean,
    scope: ReorderableCollectionItemScope,  // recebe o scope
    onTap: () -> Unit,
) {
    Box(
        modifier = with(scope) {
            Modifier
                .clickable(onClick = onTap)
                .longPressDraggableHandle()
        }
    ) { ... }
}

// No LazyColumn:
ReorderableItem(reorderState, key = item.key) { isDragging ->
    DashboardEditItemWrapper(
        item = item,
        isDragging = isDragging,
        scope = this,  // 'this' é ReorderableCollectionItemScope
        onTap = { modalManager.show(...) },
    )
}
```

---

## `onMove` — Regras e armadilhas

### Regra crítica: atualizar sincronamente

```kotlin
// CORRETO — atualiza antes de suspender
rememberReorderableLazyListState(lazyListState) { from, to ->
    list = list.toMutableList().apply { add(to.index, removeAt(from.index)) }
    // haptic pode ser chamado depois (não afeta a posição visual)
    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
}

// ERRADO — atraso causa flickering
rememberReorderableLazyListState(lazyListState) { from, to ->
    delay(100)                             // qualquer suspensão antes do update = flickering
    list = list.toMutableList().apply { ... }
}
```

Para a `DashboardViewModel`, `MoveComponent` atualiza `_editingState.value` diretamente (StateFlow, sem suspensão) — é seguro chamar dentro de `onMove`.

### Lookup por key (mais seguro que por index)

Quando há itens não-reordenáveis na lista (headers, footers), o `from.index` inclui esses itens e causa off-by-one errors. Prefira lookup por key:

```kotlin
rememberReorderableLazyListState(lazyListState) { from, to ->
    list = list.toMutableList().apply {
        val fromIndex = indexOfFirst { it.key == from.key }
        val toIndex   = indexOfFirst { it.key == to.key }
        if (fromIndex != -1 && toIndex != -1) {
            add(toIndex, removeAt(fromIndex))
        }
    }
}
```

No nosso caso (lista editável sem headers), `from.index` e `to.index` são corretos. Use key lookup se adicionar headers futuramente.

---

## Propriedades de `ReorderableLazyListState`

```kotlin
val reorderState: ReorderableLazyListState

reorderState.isAnyItemDragging    // Boolean — true se qualquer item está sendo arrastado
reorderState.draggingItemKey      // Any? — key do item sendo arrastado
reorderState.draggingItemDraggedDelta  // Offset — delta acumulado desde o início do drag
```

`isAnyItemDragging` é útil para alterar o visual do container durante o drag (ex: mudar background).

---

## Haptic Feedback — padrão recomendado

```kotlin
val haptic = LocalHapticFeedback.current

// onDragStarted
haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)

// onMove (cada vez que dois itens trocam de lugar)
haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)

// onDragStopped
haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
```

`GestureThresholdActivate` e `SegmentFrequentTick` requerem Android API 34+; são no-op em versões anteriores. Em iOS e Desktop, `LocalHapticFeedback` usa as APIs nativas disponíveis.

---

## Armadilhas conhecidas

| Problema | Causa | Solução |
|---------|-------|---------|
| Item volta para posição original com flicker | `onMove` suspendeu antes de atualizar a lista | Atualizar estado sincronamente antes de qualquer suspensão |
| Drag não funciona | Key de `ReorderableItem` diferente da key em `items()` | Usar a mesma expressão de key nos dois lugares |
| Compile error nos modifiers de drag | Chamando fora de `ReorderableCollectionItemScope` | Passar `this` (o scope) para o composable filho |
| Vizinhos não animam durante drag | `animateItemModifier` sobrescrito com `Modifier` | Não sobrescrever — default `Modifier.animateItem()` deve ser mantido |
| Cliques param de funcionar após aplicar drag | Usando `draggableHandle()` no elemento inteiro | Trocar por `longPressDraggableHandle()` quando o elemento também é clicável |
| Off-by-one em `from.index`/`to.index` | Lista tem items não-reordenáveis (headers) antes dos itens | Usar key lookup ao invés de index |