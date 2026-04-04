# Dashboard Edit Mode — Step 3: Known Issues

## Issue 1 — Drag cancela ao transitar um componente entre seções

**Severidade:** Crítica — resolvida

**Descrição:**
Ao arrastar um componente entre a seção ativa e a seção disponível (em ambas as direções), o gesto era interrompido imediatamente ao cruzar o `section_header`.

**Causa raiz:**
O `DashboardEditItemWrapper` aplicava o `clickable` de forma condicional via `then(if (isActive) Modifier.clickable(...) else Modifier)`. Quando o item cruzava o `section_header` e `isActive` mudava durante o drag, a recomposição **adicionava ou removia estruturalmente um nó `pointerInput`** da cadeia de modifiers. O Compose reconstrói a cadeia de dispatch de eventos de ponteiro ao detectar mudanças estruturais, o que interrompia o gesto do `longPressDraggableHandle` em andamento.

**Correção:**
Substituir o modifier condicional por `clickable(enabled = isActive, onClick = onTap)`. Com `enabled` como parâmetro, o **nó `pointerInput` permanece estruturalmente estável** na cadeia durante toda a duração do drag — apenas o seu comportamento interno muda. A cadeia de dispatch não é reconstruída, e o gesto não é interrompido.

```kotlin
// Antes — remove/adiciona o nó pointerInput mid-drag → cancela o gesto
.then(if (isActive) Modifier.clickable(onClick = onTap) else Modifier)

// Depois — nó estável, só o estado enabled muda
.clickable(enabled = isActive, onClick = onTap)
```

**Observação sobre recorrência:**
`then(if (condition) Modifier.someGesture(...) else Modifier)` parece equivalente a `someGesture(enabled = condition)`, mas não é. O primeiro muda a *estrutura* da cadeia de modifiers; o segundo muda apenas o *estado* de um nó existente. Qualquer modifier que hospede gestos em andamento (drag, scroll, long press) deve ser mantido estruturalmente estável durante recomposições para evitar interrupção do gesto.

---

## Issue 2 — Impossível mover componente para seção vazia

**Severidade:** Crítica — resolvida

**Descrição:**
Quando todos os componentes estavam ativos (seção disponível vazia) era impossível desativar qualquer um deles. Da mesma forma, quando todos estavam inativos (seção ativa vazia), era impossível ativar qualquer um. Mesmo com itens em ambas as seções, não era possível posicionar um componente logo após o `section_header` sem trocar de posição com outro componente — o único caminho de transição era via componente adjacente, nunca diretamente pela divisória.

**Causa raiz:**
`ReorderableItem(enabled = false)` no `section_header` e `available_placeholder`. A documentação da biblioteca sugere que `enabled = false` cria um item "não arrastável mas destino válido". Na prática, `enabled = false` exclui o item **completamente** do sistema de reordenação — ele não dispara `onMove` nem como `to`. Com isso, a divisória era invisível para o mecanismo de drag e não contava como ponto de cruzamento. Quando a seção de destino estava vazia, nenhum `onMove` disparava ao cruzar a fronteira.

**Correção:**
Remover `enabled = false` dos `ReorderableItem` do `section_header` e do `available_placeholder`. Sem `draggableHandle`, esses itens não podem ser iniciados como drag (`from`), mas permanecem destinos válidos (`to`). O `onMove` agora dispara `(componente, section_header)` ao cruzar a divisória em qualquer direção, independentemente de a seção de destino estar vazia ou não. A guarda `if (fromKey == "section_header" || fromKey == "available_placeholder") return` no callback protege contra o caso teórico de serem `from`.

```kotlin
// Antes — exclui completamente do sistema de drop
ReorderableItem(reorderState, key = "section_header", enabled = false) { ... }

// Depois — destino válido, não arrastável por ausência de handle
ReorderableItem(reorderState, key = "section_header") { ... }
```
