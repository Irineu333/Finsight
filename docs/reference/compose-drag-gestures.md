# Referência: Drag Gestures em Compose Multiplatform

Referência para implementar drag cross-container (AddComponentPanel → lista editável) na Etapa 3.

---

## `detectDragGesturesAfterLongPress`

```kotlin
suspend fun PointerInputScope.detectDragGesturesAfterLongPress(
    onDragStart: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
)
```

**Comportamento:** aguarda long press antes de iniciar o drag. Tap curto não dispara `onDragStart`. Compatível com Compose Multiplatform (Android, iOS, Desktop).

**Uso típico:**

```kotlin
Modifier.pointerInput(Unit) {
    detectDragGesturesAfterLongPress(
        onDragStart = { startOffset ->
            dragState.startDrag(key = item.key, startOffset = startOffset)
        },
        onDrag = { change, dragAmount ->
            change.consume()
            dragState.updateDrag(dragAmount)
        },
        onDragEnd = {
            val insertAt = dragState.endDrag()
            onAction(AddComponent(dragState.draggedKey!!, insertAt))
        },
        onDragCancel = {
            dragState.cancelDrag()
        },
    )
}
```

---

## `detectDragGestures`

```kotlin
suspend fun PointerInputScope.detectDragGestures(
    onDragStart: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
)
```

Inicia imediatamente ao toque (sem long press). Para o `DashboardAddItemCard`, usar `detectDragGesturesAfterLongPress` para diferenciar tap (adicionar) de drag (posicionar).

---

## Parâmetros dos callbacks

| Parâmetro | Tipo | Descrição |
|-----------|------|-----------|
| `startOffset` (em `onDragStart`) | `Offset` | Posição no espaço de coordenadas local do `pointerInput` onde o drag iniciou |
| `change` (em `onDrag`) | `PointerInputChange` | Evento de ponteiro atual — chamar `change.consume()` impede que eventos pais processem o mesmo toque |
| `dragAmount` (em `onDrag`) | `Offset` | **Delta** de movimento desde o último evento (não posição absoluta) |

---

## Rastrear posição absoluta durante drag

`dragAmount` é um delta relativo. Para rastrear a posição absoluta (para o ghost preview flutuante):

```kotlin
// No DragToAddState:
var dragOffset by mutableStateOf(Offset.Zero)

fun updateDrag(delta: Offset) {
    dragOffset += delta
}
```

```kotlin
// No Modifier:
onDragStart = { startOffset ->
    dragState.dragOffset = startOffset  // posição inicial
    dragState.startDrag(item.key)
},
onDrag = { change, dragAmount ->
    change.consume()
    dragState.updateDrag(dragAmount)    // acumula o delta
},
```

---

## Coordenadas locais vs globais

Para calcular o `dropTargetIndex` na lista acima, a lista precisa saber em qual posição do seu espaço de coordenadas o ghost está.

### `Modifier.onGloballyPositioned`

```kotlin
var listGlobalOffset = Offset.Zero
var listHeight = 0

Modifier.onGloballyPositioned { coordinates ->
    listGlobalOffset = coordinates.positionInRoot()
    listHeight = coordinates.size.height
}
```

### Converter dragOffset (global) → local da lista

```kotlin
val localY = dragState.dragOffset.y - listGlobalOffset.y
val dropIndex = (localY / itemHeight).toInt().coerceIn(0, items.size)
```

---

## Ghost preview flutuante

O preview flutuante segue o ponteiro durante o drag. Renderizado como `Box` absoluto no container pai:

```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    // 1. Lista editável
    LazyColumn(modifier = Modifier.onGloballyPositioned { ... }) { ... }

    // 2. Painel de adição
    AddComponentPanel(...)

    // 3. Ghost preview — visível apenas durante drag cross-container
    if (dragState.isDragging) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        dragState.dragOffset.x.roundToInt(),
                        dragState.dragOffset.y.roundToInt(),
                    )
                }
                .size(200.dp, 80.dp)
                .alpha(0.85f)
                .shadow(8.dp, RoundedCornerShape(8.dp)),
        ) {
            dragState.draggedKey?.let { key ->
                DashboardComponentContent(
                    component = DashboardComponentMocks.forKey(key) ?: return@let
                )
            }
        }
    }
}
```

---

## `DragToAddState` — implementação completa

```kotlin
// ui/screen/dashboard/DragToAddState.kt
class DragToAddState {
    var isDragging by mutableStateOf(false)
        private set
    var draggedKey by mutableStateOf<String?>(null)
        private set
    var dragOffset by mutableStateOf(Offset.Zero)
        private set
    var dropTargetIndex by mutableStateOf<Int?>(null)

    fun startDrag(key: String, startOffset: Offset) {
        isDragging = true
        draggedKey = key
        dragOffset = startOffset
        dropTargetIndex = null
    }

    fun updateDrag(delta: Offset) {
        dragOffset += delta
    }

    fun endDrag(): Int? {
        val idx = dropTargetIndex
        reset()
        return idx
    }

    fun cancelDrag() {
        reset()
    }

    private fun reset() {
        isDragging = false
        draggedKey = null
        dragOffset = Offset.Zero
        dropTargetIndex = null
    }
}

val LocalDashboardDragState = staticCompositionLocalOf { DragToAddState() }
```

---

## `consume()` — quando usar

`change.consume()` marca o evento como consumido, impedindo que composables pais respondam ao mesmo toque.

```kotlin
onDrag = { change, dragAmount ->
    change.consume()  // evita scroll acidental da LazyColumn durante drag
    dragState.updateDrag(dragAmount)
},
```

Omitir `consume()` pode causar comportamento inesperado quando o drag item está dentro de um container scrollável.

---

## Desktop (mouse drag)

`detectDragGesturesAfterLongPress` também funciona com mouse no Desktop. Em Desktop:
- Long press com mouse ≈ pressionar e manter por ~500ms
- O delta em `onDrag` reflete o movimento do mouse

Nenhuma implementação `expect/actual` necessária — a API é idêntica em todas as plataformas Compose Multiplatform.