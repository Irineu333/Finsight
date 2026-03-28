# Spec: Dashboard Customizável — Modo Edição

## 1. Contexto

A dashboard possui 9 componentes fixos renderizados em sequência. Esta feature permite ao usuário personalizar quais componentes aparecem e em qual ordem, via um modo de edição acionado por long press.

**Estado atual da branch `feature/dashboard-edit-mode`:**
- Arquitetura componentizada já existe (`DashboardComponent` sealed interface com 9 tipos)
- Sem modo edição implementado (branch está no estado pós-refactor de componentização)
- `DashboardPreferencesRepository` e `DashboardComponentRegistry` existem apenas em histórico git (WIP anterior resetado)

---

## 2. Componentes Disponíveis

| Key | Componente | Descrição |
|-----|-----------|-----------|
| `total_balance` | TotalBalance | Saldo total consolidado |
| `balance_stats_concrete` | ConcreteBalanceStats | Receitas e despesas do mês |
| `balance_stats_pending` | PendingBalanceStats | Pendências de recorrentes |
| `accounts_overview` | AccountsOverview | Lista de contas com saldos |
| `credit_cards_pager` | CreditCardsPager | Pager de cartões com faturas |
| `spending_pager` | SpendingPager | Gastos por categoria e orçamentos |
| `pending_recurring` | PendingRecurring | Recorrentes pendentes de confirmação |
| `recents` | Recents | Últimas 4 transações |
| `quick_actions` | QuickActions | Atalhos de navegação |

Todos removíveis. Ordem e visibilidade são definidas pelo usuário.

---

## 3. Fluxo de UX

```
Dashboard normal
    │
    ├─ Long press em qualquer componente
    │       │
    │       ▼
    │   Entra em Edit Mode
    │   - Bottom nav oculta (AnimatedVisibility)
    │   - Top bar troca por barra de edição (Cancelar | "Editar" | Confirmar)
    │   - Componentes ganham drag handle + botão remover
    │   - Transição suave (spring animation)
    │
    ├─ Drag handle → reordena (drag and drop na lista)
    │
    ├─ Botão "–" no componente → remove (animação de saída)
    │
    ├─ Botão "+" flutuante (FAB)
    │       │
    │       ▼
    │   Add Component Panel desliza do bottom
    │   - Mostra componentes disponíveis (não presentes)
    │   - Tap para adicionar no final da lista
    │   - Long press + drag → arrasta para posição específica na lista acima
    │
    ├─ "Cancelar" → descarta alterações, volta ao estado original
    └─ "Confirmar" → persiste nova ordem/composição, sai do edit mode
```

---

## 4. Arquitetura — Visão Geral

```
Domain
  DashboardComponentPreference        ← model de preferência
  IDashboardPreferencesRepository     ← interface

Data (database/)
  DashboardPreferencesRepository      ← persiste em Settings (JSON)

UI (screen/dashboard/)
  DashboardComponentRegistry          ← registro de todos os componentes disponíveis
  DashboardComponentMocks             ← dados de exemplo para preview no edit mode
  DashboardUiState (Loading | Viewing | Editing) ← estados como tipos distintos
  DashboardAction                     ← ações de edição
  DashboardViewModel                  ← orquestra preferências + dados reais
  DashboardScreen                     ← renderiza normal e edit mode
  AddComponentPanel                   ← painel in-tree de adição (não é Modal)
  DragToAddState                      ← estado global de drag cross-container
```

---

## 5. Domain Layer

### 5.1 `DashboardComponentPreference`

```kotlin
// domain/model/DashboardComponentPreference.kt
data class DashboardComponentPreference(
    val key: String,
    val position: Int,
)
```

A preferência armazena apenas os componentes **visíveis** em ordem. Componentes ausentes da lista estão disponíveis para adicionar.

### 5.2 `IDashboardPreferencesRepository`

```kotlin
// domain/repository/IDashboardPreferencesRepository.kt
interface IDashboardPreferencesRepository {
    fun observe(): Flow<List<DashboardComponentPreference>>
    suspend fun save(preferences: List<DashboardComponentPreference>)
}
```

Sem `currentPreferences()` síncrono — o ViewModel usa `first()` quando precisar do valor atual.

---

## 6. Data Layer

### 6.1 `DashboardPreferencesRepository`

```kotlin
// database/repository/DashboardPreferencesRepository.kt
class DashboardPreferencesRepository(
    private val settings: Settings,
) : IDashboardPreferencesRepository {

    private val _preferences = MutableStateFlow(load())

    override fun observe(): Flow<List<DashboardComponentPreference>> = _preferences

    override suspend fun save(preferences: List<DashboardComponentPreference>) {
        val json = Json.encodeToString(preferences.map { it.toSerializable() })
        settings.putString(KEY, json)
        _preferences.value = preferences
    }

    private fun load(): List<DashboardComponentPreference> {
        val json = settings.getStringOrNull(KEY) ?: return emptyList()
        return runCatching {
            Json.decodeFromString<List<SerializablePreference>>(json)
                .map { it.toDomain() }
        }.getOrDefault(emptyList())
    }

    @Serializable
    private data class SerializablePreference(val key: String, val position: Int)

    companion object {
        private const val KEY = "dashboard_preferences"
    }
}
```

---

## 7. UI Layer

### 7.1 `DashboardComponentRegistry`

```kotlin
// ui/screen/dashboard/DashboardComponentRegistry.kt
data class DashboardRegistryEntry(
    val key: String,
    val title: UiText,
    val defaultPosition: Int,
)

object DashboardComponentRegistry {
    val entries: List<DashboardRegistryEntry> = listOf(
        DashboardRegistryEntry("total_balance",          UiText.Res(Res.string.component_total_balance),        0),
        DashboardRegistryEntry("balance_stats_concrete", UiText.Res(Res.string.component_balance_stats),        1),
        DashboardRegistryEntry("balance_stats_pending",  UiText.Res(Res.string.component_pending_balance),      2),
        DashboardRegistryEntry("accounts_overview",      UiText.Res(Res.string.component_accounts_overview),    3),
        DashboardRegistryEntry("credit_cards_pager",     UiText.Res(Res.string.component_credit_cards),         4),
        DashboardRegistryEntry("spending_pager",         UiText.Res(Res.string.component_spending),             5),
        DashboardRegistryEntry("pending_recurring",      UiText.Res(Res.string.component_pending_recurring),    6),
        DashboardRegistryEntry("recents",                UiText.Res(Res.string.component_recents),              7),
        DashboardRegistryEntry("quick_actions",          UiText.Res(Res.string.component_quick_actions),        8),
    )

    fun defaultPreferences(): List<DashboardComponentPreference> =
        entries.map { DashboardComponentPreference(it.key, it.defaultPosition) }

    fun titleFor(key: String): UiText =
        entries.find { it.key == key }?.title ?: UiText.Raw(key)
}
```

### 7.2 `DashboardComponentMocks`

Fornece instâncias estáticas de cada componente com dados de exemplo para preview no edit mode.

```kotlin
// ui/screen/dashboard/DashboardComponentMocks.kt
object DashboardComponentMocks {
    val totalBalance = DashboardComponent.TotalBalance(amount = 5_450.00)
    val concreteBalanceStats = DashboardComponent.ConcreteBalanceStats(income = 8_200.00, expense = 2_750.00)
    val pendingBalanceStats = DashboardComponent.PendingBalanceStats(pendingIncome = 1_200.00, pendingExpense = 350.00)
    val accountsOverview = DashboardComponent.AccountsOverview(accounts = mockAccounts())
    val creditCardsPager = DashboardComponent.CreditCardsPager(creditCards = mockCreditCards())
    val spendingPager = DashboardComponent.SpendingPager(categorySpending = mockSpending(), budgetProgress = mockBudgets())
    val pendingRecurring = DashboardComponent.PendingRecurring(recurringList = mockRecurring())
    val recents = DashboardComponent.Recents(operations = mockOperations(), hasMore = true)
    val quickActions = DashboardComponent.QuickActions(actions = QuickActionType.entries)

    fun forKey(key: String): DashboardComponent? = when (key) {
        "total_balance"          -> totalBalance
        "balance_stats_concrete" -> concreteBalanceStats
        "balance_stats_pending"  -> pendingBalanceStats
        "accounts_overview"      -> accountsOverview
        "credit_cards_pager"     -> creditCardsPager
        "spending_pager"         -> spendingPager
        "pending_recurring"      -> pendingRecurring
        "recents"                -> recents
        "quick_actions"          -> quickActions
        else                     -> null
    }

    // private mock factories...
}
```

### 7.3 `DashboardUiState` — sealed class

O modo de visualização e o modo de edição são tipos distintos do UiState, seguindo o mesmo padrão de `BudgetsUiState`, `AccountsUiState` e demais screens do projeto.

```kotlin
// ui/screen/dashboard/DashboardUiState.kt
sealed class DashboardUiState {
    abstract val yearMonth: YearMonth

    data class Loading(
        override val yearMonth: YearMonth = Clock.System.now().toYearMonth(),
    ) : DashboardUiState()

    data class Viewing(
        override val yearMonth: YearMonth,
        val components: List<DashboardComponent>,
    ) : DashboardUiState()

    data class Editing(
        override val yearMonth: YearMonth,
        val items: List<DashboardEditItem>,          // componentes atualmente na dashboard
        val availableItems: List<DashboardEditItem>, // disponíveis para adicionar
    ) : DashboardUiState()
}

data class DashboardEditItem(
    val key: String,
    val title: UiText,
    val preview: DashboardComponent,                 // instância mock para renderização
)
```

- `Loading` — carregamento inicial antes dos repositórios emitirem
- `Viewing` — modo normal, componentes reais com dados ao vivo
- `Editing` — modo edição, componentes são previews (mock data), editáveis pelo usuário

`yearMonth` é `abstract` pois aparece no seletor de mês em ambos os modos visíveis.

### 7.4 `DashboardAction` — expansão

```kotlin
// ui/screen/dashboard/DashboardAction.kt
sealed class DashboardAction {
    data class AdjustBalance(val target: Double) : DashboardAction()

    // Edit mode
    data object EnterEditMode : DashboardAction()
    data object ConfirmEdit : DashboardAction()
    data object CancelEdit : DashboardAction()
    data class MoveComponent(val from: Int, val to: Int) : DashboardAction()
    data class RemoveComponent(val key: String) : DashboardAction()
    data class AddComponent(val key: String, val insertAt: Int? = null) : DashboardAction()
}
```

`insertAt = null` insere no final da lista.

### 7.5 `DashboardViewModel` — novas responsabilidades

`_editingState` é um `MutableStateFlow<DashboardUiState.Editing?>` separado que, quando não-nulo, tem prioridade sobre o estado reativo dos repositórios. Isso congela a UI durante a edição sem cancelar os flows de dados.

```kotlin
class DashboardViewModel(
    // ... repositórios existentes ...
    private val dashboardPreferencesRepository: IDashboardPreferencesRepository,
    private val dashboardComponentsBuilder: DashboardComponentsBuilder,
) : ViewModel() {

    // Snapshot para suportar CancelEdit sem re-salvar
    private var preferencesSnapshot: List<DashboardComponentPreference> = emptyList()

    // Editing state — quando não-nulo, sobrescreve o Viewing reativo
    private val _editingState = MutableStateFlow<DashboardUiState.Editing?>(null)

    // Flow reativo que sempre produz Loading → Viewing
    private val viewingState: Flow<DashboardUiState> = combine(
        dashboardPreferencesRepository.observe(),
        // ... demais flows de repositórios ...
    ) { preferences, /* ... */ ->
        val ordered = applyPreferences(preferences, builtComponents)
        DashboardUiState.Viewing(yearMonth = targetMonth, components = ordered)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        _editingState,
        viewingState,
    ) { editing, viewing ->
        editing ?: viewing                          // Editing tem prioridade sobre Viewing
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState.Loading(),
    )

    fun onAction(action: DashboardAction) = when (action) {
        is EnterEditMode   -> enterEditMode()
        is ConfirmEdit     -> confirmEdit()
        is CancelEdit      -> cancelEdit()
        is MoveComponent   -> moveComponent(action.from, action.to)
        is RemoveComponent -> removeComponent(action.key)
        is AddComponent    -> addComponent(action.key, action.insertAt)
        is AdjustBalance   -> { /* ... existente ... */ }
    }

    private fun enterEditMode() {
        val current = uiState.value as? DashboardUiState.Viewing ?: return
        preferencesSnapshot = current.components
            .mapIndexed { i, c -> DashboardComponentPreference(c.key, i) }
        _editingState.value = buildEditingState(current)
    }

    private fun confirmEdit() {
        viewModelScope.launch {
            val editing = _editingState.value ?: return@launch
            val prefs = editing.items.mapIndexed { i, item -> DashboardComponentPreference(item.key, i) }
            dashboardPreferencesRepository.save(prefs)
            _editingState.value = null
        }
    }

    private fun cancelEdit() {
        viewModelScope.launch {
            dashboardPreferencesRepository.save(preferencesSnapshot)
            _editingState.value = null
        }
    }

    private fun moveComponent(from: Int, to: Int) {
        val current = _editingState.value ?: return
        val items = current.items.toMutableList()
        items.add(to, items.removeAt(from))
        _editingState.value = current.copy(items = items)
    }

    private fun removeComponent(key: String) {
        val current = _editingState.value ?: return
        val removed = current.items.find { it.key == key } ?: return
        _editingState.value = current.copy(
            items = current.items.filter { it.key != key },
            availableItems = current.availableItems + removed,
        )
    }

    private fun addComponent(key: String, insertAt: Int?) {
        val current = _editingState.value ?: return
        val added = current.availableItems.find { it.key == key } ?: return
        val newItems = current.items.toMutableList().also { list ->
            if (insertAt != null) list.add(insertAt.coerceIn(0, list.size), added)
            else list.add(added)
        }
        _editingState.value = current.copy(
            items = newItems,
            availableItems = current.availableItems.filter { it.key != key },
        )
    }

    private fun applyPreferences(
        preferences: List<DashboardComponentPreference>,
        all: List<DashboardComponent>,
    ): List<DashboardComponent> {
        if (preferences.isEmpty()) return all
        val byKey = all.associateBy { it.key }
        return preferences.sortedBy { it.position }.mapNotNull { byKey[it.key] }
    }
}
```

---

## 8. UI — DashboardScreen

### 8.1 Estrutura geral do DashboardScreen

O `DashboardScreen` faz `when(uiState)` e renderiza estruturas distintas para cada tipo:

```kotlin
when (val state = uiState) {
    is DashboardUiState.Loading  -> DashboardLoadingContent()
    is DashboardUiState.Viewing  -> DashboardViewingContent(state, onAction)
    is DashboardUiState.Editing  -> DashboardEditingContent(state, onAction)
}
```

**Estrutura do `DashboardEditingContent`:**

```
Box (fill max size)
├── LazyColumn (lista editável)
│   └── items(state.items) { item ->
│       DashboardEditItemCard(
│           item = item,
│           dragHandle = { ReorderHandle() },
│           onRemove = { onAction(RemoveComponent(item.key)) },
│       )
│   }
├── AddComponentPanel (AnimatedVisibility, slide from bottom)
│   └── items(state.availableItems) { item ->
│       DashboardAvailableItemCard(
│           item = item,
│           onTap = { onAction(AddComponent(item.key)) },
│           onDragStart = { dragState.startDrag(item.key) },
│       )
│   }
└── DragPreview (visível enquanto dragState.isDragging)
    └── renderiza o componente mock em tamanho reduzido
```

### 8.2 `DashboardEditItemCard`

- Exibe o componente mock (`item.preview`) em tamanho normal, **não interativo** (pointer intercept = blocked)
- Overlay com 40% de opacidade para comunicar estado não-ativo
- Drag handle no canto superior direito (ícone `drag_handle`)
- Botão "–" circular no canto superior esquerdo
- Borda arredondada levemente elevada (card visual)
- Spring animation na entrada/saída (`fadeIn` + `scaleIn` / `fadeOut` + `scaleOut`)

### 8.3 Transição Normal ↔ Edit Mode

- `AnimatedContent(targetState = uiState)` com `slideInVertically` para a edit toolbar no topo
- `AnimatedVisibility` para ocultar a `BottomNavigationBar` no `HomeScreen`

A detecção do modo edição no `HomeScreen` é feita via callback:

```kotlin
// HomeScreen observa o uiState do DashboardViewModel
val isEditMode = dashboardUiState is DashboardUiState.Editing

AnimatedVisibility(visible = !isEditMode) {
    BottomNavigationBar(...)
}
```

### 8.4 Edit Toolbar

```
[ Cancelar ]   ────── Editar ──────   [ Confirmar ]
```

Substitui a TopAppBar normal via `AnimatedContent`.

---

## 9. AddComponentPanel

**Não é um `ModalBottomSheet` do `ModalManager`.** É um overlay in-tree renderizado dentro do `DashboardScreen`, necessário para compartilhar o espaço de coordenadas com a lista de componentes e viabilizar o drag cross-container.

```
╔══════════════════════════════╗
║  [Dashboard em edit mode]    ║  ← área de drop (dashboard list)
║                              ║
║  ┌─ drop indicator ─────┐   ║
║                              ║
╠══════════════════════════════╣  ← linha de separação animada
║  Adicionar componente    [✕] ║
║  ┌──────┐ ┌──────┐ ┌──────┐ ║
║  │Total │ │Cartão│ │Gastos│ ║
║  │Saldo │ │ Pager│ │      │ ║
║  └──────┘ └──────┘ └──────┘ ║
╚══════════════════════════════╝
```

- Painel ocupa ~50% da altura da tela
- Dashboard list ocupa o espaço restante acima
- `AnimatedVisibility(slideInVertically { it } / slideOutVertically { it })`
- Abre ao clicar no FAB "+" da edit toolbar
- Grid de 2 colunas com previews em miniatura dos componentes disponíveis

---

## 10. Drag & Drop

### 10.1 Reordenação na lista (in-list drag)

**Biblioteca:** `sh.calvin.reorderable:reorderable` (Compose Multiplatform compatível)

```kotlin
// build.gradle.kts (composeApp)
implementation("sh.calvin.reorderable:reorderable:2.4.3")
```

```kotlin
val reorderState = rememberReorderableLazyListState(
    onMove = { from, to -> onAction(MoveComponent(from.index, to.index)) }
)

LazyColumn(state = reorderState.listState) {
    items(state.items, key = { it.key }) { item ->  // state: DashboardUiState.Editing
        ReorderableItem(reorderState, key = item.key) { isDragging ->
            DashboardEditItemCard(
                item = item,
                dragHandle = {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        modifier = Modifier.draggableHandle(),
                    )
                },
                isDragging = isDragging,
            )
        }
    }
}
```

### 10.2 Drag do painel para a lista (cross-container drag)

**Abordagem:** `DragToAddState` com `pointerInput`.

```kotlin
// ui/screen/dashboard/DragToAddState.kt
class DragToAddState {
    var isDragging by mutableStateOf(false)
    var draggedKey by mutableStateOf<String?>(null)
    var dragOffset by mutableStateOf(Offset.Zero)
    var dropTargetIndex by mutableStateOf<Int?>(null)

    fun startDrag(key: String, startOffset: Offset) { ... }
    fun updateDrag(delta: Offset) { dragOffset += delta }
    fun endDrag(): Int? { val idx = dropTargetIndex; reset(); return idx }
    fun cancelDrag() { reset() }
    private fun reset() { isDragging = false; draggedKey = null; dragOffset = Offset.Zero; dropTargetIndex = null }
}

val LocalDashboardDragState = staticCompositionLocalOf { DragToAddState() }
```

**Fluxo:**
1. Long press em `DashboardAvailableItemCard` → `dragState.startDrag(key, offset)`
2. Durante drag: a lista acima detecta `dragState.dragOffset` via `Modifier.onGloballyPositioned` e calcula `dropTargetIndex`
3. Ghost preview renderizado no `Box` pai na posição `dragOffset`
4. Release: `onAction(AddComponent(dragState.draggedKey!!, insertAt = dragState.endDrag()))`

**Desktop:** `detectDragGesturesAfterLongPress` funciona com mouse drag no Desktop sem mudanças adicionais.

---

## 11. Configurações de Componentes (V1)

**Nenhum componente possui configurações na V1.**

A arquitetura deve suportar configurações sem implementar nenhuma:
- `DashboardEditItemCard` recebe `onSettings: (() -> Unit)?`
- Ícone de engrenagem aparece **apenas se** `onSettings != null`
- Na V1, todos os componentes passam `onSettings = null`

**Candidato para V2:** QuickActions — permitir ocultar/reordenar ações individuais.

---

## 12. Persistência — Comportamento

| Cenário | Comportamento |
|---------|--------------|
| Primeira abertura | Usa `DashboardComponentRegistry.defaultPreferences()` |
| Preferências salvas | Aplica ordem e filtra componentes ausentes |
| Novo componente adicionado no app (futuro) | Aparece no final da lista por ser ausente das preferências |
| Componente removido do app (futuro) | Ignorado silenciosamente ao carregar |

---

## 13. DI

```kotlin
// RepositoryModule.kt
single<IDashboardPreferencesRepository> {
    DashboardPreferencesRepository(settings = get())
}

// ViewModelModule.kt
viewModel {
    DashboardViewModel(
        // ... existentes ...
        dashboardPreferencesRepository = get(),
    )
}
```

---

## 14. Strings necessárias (`strings.xml`)

```xml
<string name="component_total_balance">Saldo Total</string>
<string name="component_balance_stats">Receitas e Despesas</string>
<string name="component_pending_balance">Balanço Pendente</string>
<string name="component_accounts_overview">Contas</string>
<string name="component_credit_cards">Cartões de Crédito</string>
<string name="component_spending">Gastos por Categoria</string>
<string name="component_pending_recurring">Recorrentes Pendentes</string>
<string name="component_recents">Transações Recentes</string>
<string name="component_quick_actions">Ações Rápidas</string>
<string name="edit_mode_confirm">Confirmar</string>
<string name="edit_mode_cancel">Cancelar</string>
<string name="edit_mode_title">Editar</string>
<string name="add_component_title">Adicionar componente</string>
```

---

## 15. Plataformas

| Comportamento | Android | iOS | Desktop |
|--------------|---------|-----|---------|
| Long press para entrar no edit mode | touch long press | touch long press | mouse right-click ou long press |
| Drag handle reordenação | toque + arrastar | toque + arrastar | click + arrastar (mouse) |
| Drag do painel | touch drag | touch drag | mouse drag |
| System/bottom nav ocultação | `AnimatedVisibility` | `AnimatedVisibility` | `AnimatedVisibility` |
| `sh.calvin.reorderable` | suportado | suportado | suportado |

Nenhuma implementação `expect/actual` necessária — tudo via Compose Multiplatform.

---

## 16. Fora do Escopo (V1)

- Configurações individuais de componentes (candidato V2: QuickActions)
- Múltiplos perfis de dashboard
- Sync de preferências entre devices
- Animação de "shake" no estilo iOS para os componentes no edit mode
- Undo/Redo de ações de edição

---

## 17. Decisões Técnicas

| Decisão | Alternativa descartada | Motivo |
|---------|----------------------|--------|
| `DashboardUiState` como sealed class (`Loading`, `Viewing`, `Editing`) | `data class` com `editState: EditState?` | Modo edição é um estado distinto, não uma extensão opcional do modo normal — sealed class elimina estados impossíveis e segue o padrão do projeto |
| `_editingState: MutableStateFlow<Editing?>` separado do combine reativo | Unificar tudo em um único combine | Separa responsabilidades: dados ao vivo ficam no `viewingState`, edição em curso fica no `_editingState` — evita reconstrução do estado de edição a cada emissão dos repositórios |
| `AddComponentPanel` como overlay in-tree | `ModalBottomSheet` do `ModalManager` | Drag cross-container requer espaço de coordenadas compartilhado |
| `russhwolf/settings` + JSON para persistência | Room (nova tabela) | Sem relações, sem queries — settings é suficiente e já disponível |
| `sh.calvin.reorderable` para drag in-list | `detectDragGesturesAfterLongPress` manual | API de alto nível, multiplatform, menos boilerplate |
| Preferências como lista de chaves visíveis | `(key, visible: Boolean)` | Lista menor, semântica mais clara, componentes novos aparecem automaticamente |
