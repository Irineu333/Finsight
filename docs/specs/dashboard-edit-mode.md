# Spec: Dashboard Customizável — Modo Edição

## 0. Contexto do projeto (leitura obrigatória antes de implementar)

**Package base:** `com.neoutils.finsight`

**Módulo KMP:** `composeApp` — Kotlin Multiplatform (Android / iOS / Desktop), Compose Multiplatform

**Camadas e paths relevantes:**
```
composeApp/src/commonMain/kotlin/com/neoutils/finsight/
  domain/model/          ← data classes de domínio (sem dependências externas)
  domain/repository/     ← interfaces de repositório
  database/repository/   ← implementações (Room + Settings)
  ui/screen/dashboard/   ← DashboardScreen, ViewModel, UiState, Action
  ui/component/          ← ModalManager, BottomNavigationBar, componentes compartilhados
  ui/screehome/        ← HomeScreen (navigation host, bottom nav)
  di/                    ← ViewModelModule.kt, RepositoryModule.kt
```

**Arquivos existentes que serão modificados:**
- `ui/screen/dashboard/DashboardScreen.kt` — tela principal da dashboard
- `ui/screen/dashboard/DashboardViewModel.kt` — ViewModel atual sem edit mode
- `ui/screen/dashboard/DashboardUiState.kt` — atualmente `data class`, será substituído por sealed class
- `ui/screen/dashboard/DashboardAction.kt` — atualmente só tem `AdjustBalance`
- `ui/screen/dashboard/DashboardComponent.kt` — sealed interface com 9 tipos (não modificar)
- `ui/screen/dashboard/DashboardComponentsBuilder.kt` — builder dos componentes (receberá config)
- `ui/screen/home/HomeScreen.kt` — precisa observar edit mode para ocultar bottom nav
- `di/ViewModelModule.kt` — adicionar `dashboardPreferencesRepository`
- `di/RepositoryModule.kt` — registrar `DashboardPreferencesRepository`

**Padrões obrigatórios do projeto:**
- `UiText.Res(Res.string.xxx)` para strings traduzíveis; `UiText.Raw(str)` apenas para valores dinâmicos
- `stringUiText(uiText): String` é `@Composable` — use dentro de composables
- Modals: estender `ModalBottomSheet`, mostrar via `LocalModalManager.current.show(modal)`
- DI: `viewModel {}` para ViewModels, `factory {}` para UseCases, `single {}` para Repositories
- UiState sealed: padrão `BudgetsUiState` / `AccountsUiState` — estados como tipos distintos
- Arrow `Either` / `flatMap` para error handling (não necessário nesta feature)
- Sem comentários no código — o código deve ser autoexplicativo

**Dependência a adicionar no `composeApp/build.gradle.kts`:**
```kotlin
implementation("sh.calvin.reorderable:reorderable:3.0.0")
```

**Documentação de referência das bibliotecas** (ler antes de implementar):
- [`docs/references/reorderable-library.md`](../references/reorderable-library.md) — API completa, armadilhas, `longPressDraggableHandle`, scope, `onMove` síncrono
- [`docs/references/compose-drag-gestures.md`](../references/compose-drag-gestures.md) — `detectDragGesturesAfterLongPress`, drag cross-container, `DragToAddState`
- [`docs/references/settings-library.md`](../references/settings-library.md) — `multiplatform-settings`, serialização JSON, `MutableStateFlow` bridge


---

## 1. Contexto da feature

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
    │   - Bottom nav oculta
    │   - Top bar → barra de edição (Cancelar | Editar | Confirmar)
    │   - Componentes ficam no lugar com borda sutil (indicam modo editável)
    │   - Transição suave (crossfade)
    │
    ├─ Long press + arrastar o componente → reordena por drag and drop
    │   (o componente inteiro é arrastável, sem ícone de handle)
    │
    ├─ Tap no componente → abre modal de opções
    │   - "Remover" → remove o componente (com animação de saída)
    │   - Configurações do componente (se houver)
    │
    ├─ Botão "+" na barra de edição
    │       │
    │       ▼
    │   Add Component Panel desliza do bottom
    │   - Grid de componentes disponíveis (preview em miniatura)
    │   - Tap → adiciona no final da lista
    │   - Long press + drag → arrasta para posição específica na lista
    │
    ├─ "Cancelar" → descarta alterações, volta ao estado original
    └─ "Confirmar" → persiste nova ordem/composição, sai do edit mode
```

### Anti-padrões de reordenação (explicitamente proibidos)

A reordenação **só pode acontecer por drag and drop**. Qualquer outra forma é critério de reprovação:

```
❌ Botões de seta (↑ ↓) em cada componente
❌ Botões "Mover para cima" / "Mover para baixo"
❌ Campo numérico de posição
❌ Qualquer interação que não seja arrastar fisicamente o componente
```

O `MoveComponent(from, to)` existe na `DashboardAction` exclusivamente para ser acionado pelo callback do `sh.calvin.reorderable` após um drag concluído — nunca por um botão.

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
    val config: Map<String, String> = emptyMap(),
)
```

A preferência armazena os componentes **visíveis** em ordem, e o `config` guarda configurações específicas de cada componente como um mapa genérico de strings. Componentes ausentes da lista estão disponíveis para adicionar.

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
    private data class SerializablePreference(
        val key: String,
        val position: Int,
        val config: Map<String, String> = emptyMap(),
    )

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
    val config: Map<String, String> = emptyMap(),    // carrega config salvo (para preservar em confirmEdit)
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
    data class UpdateComponentConfig(val key: String, val config: Map<String, String>) : DashboardAction()
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
        is EnterEditMode         -> enterEditMode()
        is ConfirmEdit           -> confirmEdit()
        is CancelEdit            -> cancelEdit()
        is MoveComponent         -> moveComponent(action.from, action.to)
        is RemoveComponent       -> removeComponent(action.key)
        is AddComponent          -> addComponent(action.key, action.insertAt)
        is UpdateComponentConfig -> updateComponentConfig(action.key, action.config)
        is AdjustBalance         -> { /* ... existente ... */ }
    }

    private fun enterEditMode() {
        // Lança coroutine pois precisa do primeiro valor das preferências salvas
        viewModelScope.launch {
            val current = uiState.value as? DashboardUiState.Viewing ?: return@launch
            val savedPrefs = dashboardPreferencesRepository.observe().first()
            _editingState.value = buildEditingState(current, savedPrefs)
        }
    }

    // cancelEdit NÃO re-salva no repositório — o repositório não foi modificado durante o edit mode.
    // Basta limpar _editingState e o viewingState reativo volta a comandar com as prefs anteriores.
    private fun cancelEdit() {
        _editingState.value = null
    }

    private fun confirmEdit() {
        viewModelScope.launch {
            val editing = _editingState.value ?: return@launch
            val prefs = editing.items.mapIndexed { i, item ->
                DashboardComponentPreference(key = item.key, position = i, config = item.config)
            }
            dashboardPreferencesRepository.save(prefs)
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

    private fun updateComponentConfig(key: String, config: Map<String, String>) {
        val current = _editingState.value ?: return
        _editingState.value = current.copy(
            items = current.items.map { if (it.key == key) it.copy(config = config) else it },
        )
    }

    // Constrói o EditingState a partir do Viewing atual + preferências salvas
    // Preserva configs existentes em cada item; componentes ausentes do Viewing ficam em availableItems
    private fun buildEditingState(
        viewing: DashboardUiState.Viewing,
        savedPrefs: List<DashboardComponentPreference>,
    ): DashboardUiState.Editing {
        val prefsByKey = savedPrefs.associateBy { it.key }
        val presentKeys = viewing.components.map { it.key }.toSet()

        val items = viewing.components.mapNotNull { component ->
            val entry = DashboardComponentRegistry.entries.find { it.key == component.key } ?: return@mapNotNull null
            DashboardEditItem(
                key = component.key,
                title = entry.title,
                config = prefsByKey[component.key]?.config ?: emptyMap(),
                preview = DashboardComponentMocks.forKey(component.key) ?: return@mapNotNull null,
            )
        }

        val availableItems = DashboardComponentRegistry.entries
            .filter { it.key !in presentKeys }
            .mapNotNull { entry ->
                DashboardComponentMocks.forKey(entry.key)?.let { mock ->
                    DashboardEditItem(key = entry.key, title = entry.title, preview = mock)
                }
            }

        return DashboardUiState.Editing(
            yearMonth = viewing.yearMonth,
            items = items,
            availableItems = availableItems,
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

### 8.0 Critério de aceite visual (não negociável)

> O modo edição **deve parecer o modo visualização com affordances de edição sobrepostas** — não uma lista genérica de itens com título.

O critério concreto:
- Cada componente no modo edição renderiza o **mesmo composable** que renderiza no modo normal, com dados de exemplo realistas
- Um `TotalBalance` em edit mode parece um `TotalBalance` real — com valor, cores, layout idênticos — apenas frozen (sem interações) e tappable para abrir configurações
- Um `CreditCardsPager` em edit mode parece um carrossel de cartões de crédito — não um card com o texto "Cartões de Crédito"
- Ao entrar no modo edição, o usuário deve perceber que os componentes **ficaram no lugar** enquanto o modo de interação mudou

**Modelo de interação no edit mode:**
- **Long press + drag** no componente → reordena (o componente inteiro é o handle de drag)
- **Tap** no componente → abre modal de opções (excluir, configurações futuras)
- **Sem ícone de drag handle** — não polui o visual do componente
- **Sem botão de excluir** — a exclusão fica na modal de opções

**Anti-padrões explicitamente proibidos:**
```
┌─────────────────────────────┐   ← ERRADO: lista genérica com só título
│ ☰  Saldo Total          [−] │
│ ☰  Cartões de Crédito   [−] │
│ ☰  Contas               [−] │
└─────────────────────────────┘
```

**Padrão correto:**
```
╔═════════════════════════════╗   ← borda sutil indica modo editável (ex: outline)
║                             ║
║   R$ 5.450,00               ║   ← TotalBalance renderizado com mock data
║                             ║
╚═════════════════════════════╝
  ↑ tap → abre modal de opções | long press → arrasta para reordenar

╔═════════════════════════════╗
║  ┌────────┐ ┌────────┐      ║   ← CreditCardsPager renderizado com mock data
║  │ VISA   │ │ MASTER │      ║
║  │ •••4521│ │ •••7832│      ║
╚═════════════════════════════╝
```

---

### 8.1 Estratégia de renderização

A transição entre modos deve parecer que affordances de edição **aparecem sobre** os componentes existentes — não que a tela é substituída.

**Abordagem:** `Crossfade` no nível do conteúdo principal, com ambos os modos renderizando o mesmo conjunto de itens na mesma ordem e com os mesmos tamanhos.

```kotlin
Crossfade(
    targetState = uiState,
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
) { state ->
    when (state) {
        is DashboardUiState.Loading -> DashboardLoadingContent()
        is DashboardUiState.Viewing -> DashboardViewingContent(state, onAction)
        is DashboardUiState.Editing -> DashboardEditingContent(state, onAction)
    }
}
```

Como `DashboardViewingContent` e `DashboardEditingContent` renderizam os mesmos componentes na mesma ordem e com dimensões idênticas, o `Crossfade` cria o efeito visual de affordances aparecendo/desaparecendo **sobre** os componentes em seus lugares.

---

### 8.2 `DashboardEditingContent` — estrutura

```
Box (fill max size)
├── LazyColumn (lista editável com reorderable)
│   └── items(state.items, key = { it.key }) { item ->
│       ReorderableItem { isDragging ->
│           DashboardEditItemWrapper(
│               item = item,
│               isDragging = isDragging,
│               onTap = { modalManager.show(DashboardComponentOptionsModal(item, onAction)) },
│           )
│       }
│   }
├── AddComponentPanel (AnimatedVisibility slide from bottom)
│   └── LazyVerticalGrid(2 colunas) {
│       items(state.availableItems) { item ->
│           DashboardAddItemCard(
│               item = item,
│               onTap = { onAction(AddComponent(item.key)) },
│               onDragStart = { dragState.startDrag(item.key) },
│           )
│       }
│   }
└── DragPreview (Box flutuante, visível durante drag cross-container)
```

---

### 8.3 `DashboardEditItemWrapper` — renderização fiel ao original

O componente inteiro é draggable (sem ícone de handle) e tappable (sem botão de excluir visível). O `draggableHandle()` é aplicado no wrapper inteiro.

```kotlin
// longPressDraggableHandle é extension de ReorderableCollectionItemScope.
// O scope deve ser passado explicitamente ao extrair este composable para fora do lambda de ReorderableItem.
@Composable
fun ReorderableCollectionItemScope.DashboardEditItemWrapper(
    item: DashboardEditItem,
    isDragging: Boolean,
    onTap: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isDragging) 8.dp else 0.dp, shape = RoundedCornerShape(12.dp))
            // Tap → abre modal de opções (excluir, configurações)
            .clickable(onClick = onTap)
            // Long press + drag → reordena (componente inteiro é o drag handle)
            // IMPORTANTE: usar longPressDraggableHandle (não draggableHandle) pois o item também é clicável.
            // draggableHandle() iniciaria o drag imediatamente ao toque, bloqueando os cliques.
            .longPressDraggableHandle(
                onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                onDragStopped = { haptic.performHapticFeedback(HapticFeedbackType.GestureEnd) },
            )
            // Sinaliza estado editável com borda sutil
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
    ) {
        // 1. Renderiza o composable ORIGINAL com dados mock — sem simplificação
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) { /* consome todos os eventos — componente frozen */ }
        ) {
            DashboardComponentContent(component = item.preview)  // mesmo composable do Viewing
        }

        // 2. Overlay translúcido mínimo (só para comunicar estado não-interativo)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.10f))
        )
    }
}
```

`DashboardComponentContent` é o mesmo switch `when(component)` que já existe no `DashboardViewingContent` — extraído para uma função compartilhada no mesmo arquivo ou em `DashboardComponents.kt`:

```kotlin
// Assinatura — mesmo when(component) do DashboardViewingContent, sem lógica adicional
@Composable
fun DashboardComponentContent(
    component: DashboardComponent,
    onAction: (DashboardAction) -> Unit = {},
) {
    when (component) {
        is DashboardComponent.TotalBalance        -> TotalBalanceCard(component, onAction)
        is DashboardComponent.ConcreteBalanceStats -> DashboardConcreteBalanceSection(component, onAction)
        // ... demais tipos
    }
}
```

Em `DashboardViewingContent`, substituir o `when(component)` inline por `DashboardComponentContent(component, onAction)`.
Em `DashboardEditItemWrapper`, chamar `DashboardComponentContent(item.preview)` — sem `onAction` (componente frozen).

**Resolução de conflito de gestos:** `clickable` dispara no tap-up sem movimento; `draggableHandle` só ativa após long press + movimento. Eles coexistem naturalmente sem conflito.

---

### 8.3.1 `DashboardComponentOptionsModal`

Modal de opções acionada pelo tap no componente em edit mode. Implementada como `ModalBottomSheet` do `ModalManager`.

```
╔══════════════════════════════╗
║  Saldo Total                 ║  ← título do componente (item.title)
╠══════════════════════════════╣
║  🗑  Remover                 ║  ← RemoveComponent action + dismiss modal
║                              ║
║  (configurações futuras      ║  ← V2: settings específicas do componente
║   aparecem aqui)             ║
╚══════════════════════════════╝
```

```kotlin
class DashboardComponentOptionsModal(
    private val item: DashboardEditItem,
    private val onAction: (DashboardAction) -> Unit,
) : ModalBottomSheet() {

    @Composable
    override fun Content() {
        val modalManager = LocalModalManager.current
        Column {
            Text(stringUiText(item.title), style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(Res.string.remove_component)) },
                leadingContent = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                modifier = Modifier.clickable {
                    onAction(DashboardAction.RemoveComponent(item.key))
                    modalManager.dismiss()
                },
            )
        }
    }
}
```

Em V1: apenas a opção "Remover". A estrutura já suporta adicionar opções de configuração por componente em versões futuras.

---

### 8.4 `DashboardAddItemCard` — preview no painel de adição

O painel de adição também deve remeter ao componente original, não apenas exibir seu nome. Usa uma versão em escala reduzida (`scale = 0.6f`) do mesmo composable:

```kotlin
@Composable
fun DashboardAddItemCard(
    item: DashboardEditItem,
    onTap: () -> Unit,
    onDragStart: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onTap)
            .detectDragGesturesAfterLongPress(onDragStart = { onDragStart() })
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Preview em miniatura do componente real
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(8.dp))
                .graphicsLayer { scaleX = 0.6f; scaleY = 0.6f }
                .pointerInput(Unit) { /* frozen */ }
        ) {
            DashboardComponentContent(component = item.preview)
        }

        Spacer(Modifier.height(8.dp))
        Text(stringUiText(item.title), style = MaterialTheme.typography.labelSmall)
    }
}
```

---

### 8.5 Transição Normal ↔ Edit Mode — detalhes

**Chrome (toolbar + bottom nav):**
- TopAppBar: `AnimatedContent(targetState = isEditMode)` — a toolbar normal e a de edição fazem crossfade
- BottomNavigationBar: `AnimatedVisibility(visible = !isEditMode, enter = slideInVertically { it }, exit = slideOutVertically { it })`

**Por componente:**
Não há affordances visuais explícitas (sem drag handle, sem botão remover). A borda sutil (`outlineVariant`) é o único indicador de que o componente está em modo editável. O `Crossfade` global cria a transição natural de visualização → edição.

**Ativação por long press em qualquer componente:**
```kotlin
// Aplicado no wrapper de cada componente no DashboardViewingContent
Modifier.combinedClickable(
    onLongClick = { onAction(EnterEditMode) },
    onClick = { /* interação normal do componente */ },
)
```

**Detecção no HomeScreen:**

O `HomeScreen` já instancia o `DashboardViewModel` (ou pode acessá-lo via Koin `koinViewModel()`). Basta observar o `uiState` coletado como state:

```kotlin
// HomeScreen.kt
val dashboardViewModel: DashboardViewModel = koinViewModel()
val dashboardUiState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
val isEditMode = dashboardUiState is DashboardUiState.Editing

AnimatedVisibility(
    visible = !isEditMode,
    enter = slideInVertically { it },
    exit = slideOutVertically { it },
) {
    BottomNavigationBar(...)
}
```

O `DashboardViewModel` é `single` no escopo do `NavBackStackEntry` do route `Home`, portanto a mesma instância é compartilhada entre `HomeScreen` e `DashboardScreen`.

---

### 8.6 Edit Toolbar

```
[ Cancelar ]   ────── Editar ──────   [ Confirmar ]
```

Substituí a TopAppBar normal via `AnimatedContent(targetState = isEditMode)`. Ambas as versões têm a mesma altura para evitar layout shift durante a transição.

---

## 9. AddComponentPanel

**Não é um `ModalBottomSheet` do `ModalManager`.** É um overlay in-tree renderizado dentro do `DashboardScreen`, necessário para compartilhar o espaço de coordenadas com a lista de componentes e viabilizar o drag cross-container.

```
╔══════════════════════════════╗
║  [Lista editável]            ║  ← área de drop (scroll parcial)
║                              ║
║  ┌── drop indicator ──────┐  ║
║                              ║
╠══════════════════════════════╣  ← divisor animado
║  Adicionar componente    [✕] ║
║  ┌──────────┐ ┌──────────┐  ║
║  │  ██████  │ │ ┌──┐┌──┐ │  ║  ← miniatura do TotalBalance
║  │  R$5.450 │ │ │  ││  │ │  ║  ← miniatura do CreditCardsPager
║  └──────────┘ └──────────┘  ║
║  ┌──────────┐ ┌──────────┐  ║
║  │ ▓▓ Rec.  │ │ ↑ ↓ Gastos│  ║  ← miniaturas dos demais
║  └──────────┘ └──────────┘  ║
╚══════════════════════════════╝
```

- Painel ocupa ~50% da altura da tela
- Dashboard list ocupa o espaço restante acima (scrollável para o usuário ver onde vai inserir)
- `AnimatedVisibility(enter = slideInVertically { it }, exit = slideOutVertically { it })`
- Abre ao clicar no FAB "+" flutuante no canto da edit toolbar
- Grid de 2 colunas com `DashboardAddItemCard` — preview em miniatura + título abaixo
- Os cards do painel também devem remeter ao componente original (via `DashboardComponentContent` em escala reduzida)

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
            DashboardEditItemWrapper(
                item = item,
                isDragging = isDragging,
                onTap = { modalManager.show(DashboardComponentOptionsModal(item, onAction)) },
                // draggableHandle() aplicado dentro do wrapper no componente inteiro
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
1. Long press em `DashboardAddItemCard` → `dragState.startDrag(key, offset)`
2. Durante drag: a lista acima detecta `dragState.dragOffset` via `Modifier.onGloballyPositioned` e calcula `dropTargetIndex`
3. Ghost preview renderizado no `Box` pai na posição `dragOffset`
4. Release: `onAction(AddComponent(dragState.draggedKey!!, insertAt = dragState.endDrag()))`

**Desktop:** `detectDragGesturesAfterLongPress` funciona com mouse drag no Desktop sem mudanças adicionais.

---

## 11. Configurações de Componentes

Cada componente pode ter configurações próprias acessíveis pelo tap em edit mode (`DashboardComponentOptionsModal`). As configurações são persistidas em `DashboardComponentPreference.config` como `Map<String, String>`.

Os componentes sem configurações exibem apenas a opção "Remover" na modal.

---

### 11.1 TotalBalance

Sem configurações. Sempre exibe o saldo consolidado de todas as contas.

---

### 11.2 ConcreteBalanceStats

Sem configurações. Exibe receitas e despesas reais do mês selecionado.

---

### 11.3 PendingBalanceStats

Sem configurações. Exibe pendências dos recorrentes do mês selecionado.

---

### 11.4 AccountsOverview

| Config | Chave | Tipo | Default | Opções |
|--------|-------|------|---------|--------|
| Contas excluídas da visão | `excluded_account_ids` | IDs separados por vírgula | `""` (todas) | Seleção múltipla de contas |

**Na modal:** lista de contas com toggle para incluir/excluir cada uma.

**Impacto no builder:** filtra a lista de contas antes de construir o componente, usando os IDs excluídos do config.

```kotlin
object AccountsOverviewConfig {
    const val EXCLUDED_ACCOUNT_IDS = "excluded_account_ids"
}
```

---

### 11.5 CreditCardsPager

| Config | Chave | Tipo | Default | Opções |
|--------|-------|------|---------|--------|
| Cartões excluídos da visão | `excluded_card_ids` | IDs separados por vírgula | `""` (todos) | Seleção múltipla de cartões |

**Na modal:** lista de cartões com toggle para incluir/excluir cada um.

**Impacto no builder:** filtra a lista de cartões antes de construir o componente.

```kotlin
object CreditCardsPagerConfig {
    const val EXCLUDED_CARD_IDS = "excluded_card_ids"
}
```

---

### 11.6 SpendingPager

| Config | Chave | Tipo | Default | Opções |
|--------|-------|------|---------|--------|
| Máximo de categorias exibidas | `max_categories` | Int como string | `"-1"` (todas) | 3, 5, 10, todas |

**Na modal:** seleção do limite (segmented button ou radio group).

**Impacto no builder:** aplica `.take(maxCategories)` na lista de `CategorySpending` antes de construir o componente. `-1` = sem limite.

```kotlin
object SpendingPagerConfig {
    const val MAX_CATEGORIES = "max_categories"
    const val ALL = "-1"
}
```

---

### 11.7 PendingRecurring

| Config | Chave | Tipo | Default | Opções |
|--------|-------|------|---------|--------|
| Horizonte de dias | `days_ahead` | Int como string | `"30"` | 7, 14, 30 |

**Na modal:** seleção do horizonte (segmented button ou radio group).

**Impacto no builder:** filtra recorrentes cujo próximo vencimento está dentro de `days_ahead` dias a partir de hoje.

```kotlin
object PendingRecurringConfig {
    const val DAYS_AHEAD = "days_ahead"
    const val DEFAULT_DAYS_AHEAD = 30
}
```

---

### 11.8 Recents

| Config | Chave | Tipo | Default | Opções |
|--------|-------|------|---------|--------|
| Número de transações exibidas | `count` | Int como string | `"4"` | 4, 6, 8, 10 |

**Na modal:** seleção da quantidade (segmented button).

**Impacto no builder:** aplica `.take(count)` nas operações recentes.

```kotlin
object RecentsConfig {
    const val COUNT = "count"
    const val DEFAULT_COUNT = 4
}
```

---

### 11.9 QuickActions

| Config | Chave | Tipo | Default | Opções |
|--------|-------|------|---------|--------|
| Ações ocultas | `hidden_actions` | Enum names separados por vírgula | `""` (nenhuma oculta) | BUDGETS, CATEGORIES, CREDIT_CARDS, ACCOUNTS, RECURRING, REPORTS, INSTALLMENTS |

**Na modal:** lista de todas as 7 ações com toggle para mostrar/ocultar cada uma. Pelo menos 1 ação deve permanecer visível (validação na modal).

**Impacto no builder:** filtra `QuickActionType.entries` removendo as ações ocultas.

```kotlin
object QuickActionsConfig {
    const val HIDDEN_ACTIONS = "hidden_actions"
}
```

---

### 11.10 Leitura de config no `DashboardComponentsBuilder`

O builder recebe a lista de `DashboardComponentPreference` e usa `config` ao construir cada componente:

```kotlin
// Exemplo para Recents
private fun recents(input: DashboardComponentsInput, config: Map<String, String>): DashboardComponent.Recents {
    val count = config[RecentsConfig.COUNT]?.toIntOrNull() ?: RecentsConfig.DEFAULT_COUNT
    val operations = input.operations.take(count)
    return DashboardComponent.Recents(operations = operations, hasMore = input.operations.size > count)
}
```

O `DashboardViewModel` passa o config da preferência correspondente ao construir cada componente.

---

## 11.11 Testes

Esta feature não é coberta por testes unitários. A qualidade é validada exclusivamente por critérios visuais e de experiência do usuário:
- Fidelidade dos componentes em edit mode (remete ao original)
- Suavidade da transição entre modos
- Naturalidade das interações de drag e tap

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
| `DashboardComponentContent` extraído como função compartilhada | Duplicar o `when(component)` em Viewing e Editing | Garante que edit mode renderiza EXATAMENTE o mesmo composable que o modo normal — sem risco de divergência visual |
| `Crossfade` no nível da tela para a transição de modo | `AnimatedContent` com slides | Como ambos os modos renderizam os mesmos componentes nas mesmas posições, o crossfade cria a ilusão de affordances aparecendo in-place sem custo de implementação de shared elements |
| Componentes em edit mode: composable original com mock data + overlay | Card simplificado com título | Critério de aceite — o modo edição deve remeter ao modo visualização; lista genérica com títulos é explicitamente reprovada |
| Componente inteiro como drag handle (sem ícone) | Ícone `DragHandle` dedicado | Preferência do produto: ícone de handle polui o visual do componente; long press no corpo é mais limpo e intuitivo |
| Tap no componente → modal de opções (excluir + futuras configs) | Botão "−" visível no componente | Preferência do produto: botão de excluir polui o visual; a modal centraliza opções e escala para configurações futuras sem mudar a UI do edit mode |
| `AddComponentPanel` como overlay in-tree | `ModalBottomSheet` do `ModalManager` | Drag cross-container requer espaço de coordenadas compartilhado |
| `russhwolf/settings` + JSON para persistência | Room (nova tabela) | Sem relações, sem queries — settings é suficiente e já disponível |
| `sh.calvin.reorderable` para drag in-list | `detectDragGesturesAfterLongPress` manual | API de alto nível, multiplatform, menos boilerplate |
| Preferências como lista de chaves visíveis | `(key, visible: Boolean)` | Lista menor, semântica mais clara, componentes novos aparecem automaticamente |
