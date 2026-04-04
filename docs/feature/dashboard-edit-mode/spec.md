# Spec: Dashboard Customizável — Modo Edição

> Documento alinhado com a implementação atual. Referências a etapas antigas existem apenas quando ajudam a explicar decisões de arquitetura ou trade-offs já incorporados ao código.

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
  ui/screen/home/      ← HomeScreen (navigation host, bottom nav)
  di/                    ← ViewModelModule.kt, RepositoryModule.kt
```

**Arquivos principais da feature:**
- `ui/screen/dashboard/DashboardScreen.kt` — tela principal da dashboard
- `ui/screen/dashboard/DashboardViewModel.kt` — orquestra viewing/editing, persistência e configs
- `ui/screen/dashboard/DashboardUiState.kt` — estados `Loading | Empty | Viewing | Editing`
- `ui/screen/dashboard/DashboardAction.kt` — ações de edição, drag e configuração
- `ui/screen/dashboard/DashboardComponent.kt` — sealed interface dos componentes da dashboard
- `ui/screen/dashboard/DashboardComponentsBuilder.kt` — builder dos componentes com dados reais + config
- `ui/screen/dashboard/DashboardComponentType.kt` — enum com keys e defaults
- `ui/screen/dashboard/DashboardPreviewFactory.kt` — previews realistas para edit mode
- `ui/screen/home/HomeScreen.kt` — chrome/bottom nav/FAB reagem ao edit mode
- `di/ViewModelModule.kt` — wiring de use cases e preview factory
- `di/RepositoryModule.kt` — wiring de `DashboardPreferencesRepository`

**Padrões obrigatórios do projeto:**
- `UiText.Res(Res.string.xxx)` para strings traduzíveis; `UiText.Raw(str)` apenas para valores dinâmicos
- `stringUiText(uiText): String` é `@Composable` — use dentro de composables
- Modals: estender `ModalBottomSheet`, mostrar via `LocalModalManager.current.show(modal)`
- DI: `viewModel {}` para ViewModels, `factory {}` para UseCases, `single {}` para Repositories
- UiState sealed: padrão `BudgetsUiState` / `AccountsUiState` — estados como tipos distintos
- Arrow `Either` / `flatMap` para error handling (não necessário nesta feature)
- Sem comentários no código — o código deve ser autoexplicativo

**Dependência usada pela feature (`composeApp/build.gradle.kts`):**
```kotlin
implementation("sh.calvin.reorderable:reorderable:3.0.0")
```

**Documentação de referência das bibliotecas** (ler antes de implementar):
- [`docs/reference/reorderable-library.md`](../../reference/reorderable-library.md) — API completa, armadilhas, `longPressDraggableHandle`, scope, `onMove` síncrono
- [`docs/reference/compose-drag-gestures.md`](../../reference/compose-drag-gestures.md) — `detectDragGesturesAfterLongPress`, drag cross-container, `DragToAddState`
- [`docs/reference/settings-library.md`](../../reference/settings-library.md) — `multiplatform-settings`, serialização JSON, `MutableStateFlow` bridge


---

## 1. Contexto da feature

A dashboard possui 12 componentes possíveis. O usuário pode personalizar quais componentes aparecem, em qual ordem e com quais configurações, via um modo de edição acionado por long press nos componentes renderizados ou pelo CTA explícito do estado vazio.

**Estado atual da implementação:**
- `DashboardUiState` é selado (`Loading | Empty | Viewing | Editing`)
- Preferências são persistidas em `Settings` via `DashboardPreferencesRepository`
- A ordenação e a visibilidade usam duas seções no edit mode: ativos e disponíveis
- Configurações por componente são editadas na `DashboardComponentOptionsModal`
- O fluxo final não usa mais uma ação explícita de "Remover" na modal; ativar/desativar acontece por drag entre seções

---

## 2. Componentes Disponíveis

| Key | Componente | Descrição |
|-----|-----------|-----------|
| `total_balance` | TotalBalance | Saldo total consolidado |
| `balance_stats_concrete` | ConcreteBalanceStats | Receitas e despesas do mês |
| `balance_stats_pending` | PendingBalanceStats | Balanço pendente de recorrentes |
| `balance_stats_credit_card` | CreditCardBalanceStats | Pagamentos e gastos com cartões no mês |
| `accounts_overview` | AccountsOverview | Lista de contas com saldos |
| `credit_cards_pager` | CreditCardsPager | Pager de cartões com faturas |
| `spending_by_category` | SpendingByCategory | Gastos por categoria |
| `income_by_category` | IncomeByCategory | Receitas por categoria |
| `budgets` | Budgets | Progresso de orçamentos |
| `pending_recurring` | PendingRecurring | Recorrentes pendentes de confirmação |
| `recents` | Recents | Últimas transações |
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
    │   - Top bar → barra de edição (Cancelar | título do modo edição | Confirmar)
    │   - Componentes ficam no lugar com borda sutil (indicam modo editável)
    │   - Transição suave (crossfade)
    │   - Lista dividida em duas seções por uma divisória:
    │       • Seção superior: componentes ativos (exibidos na dashboard)
    │       • Seção inferior: componentes inativos (não exibidos)
    │     A divisória é sempre visível; a seção inferior existe mesmo quando vazia
    │
    ├─ Long press + arrastar o componente → reordena por drag and drop
    │   (o componente inteiro é arrastável — o ícone de handle é puramente visual)
    │   - Arrastar da seção ativa para a inativa → desativa o componente
    │   - Arrastar da seção inativa para a ativa → ativa o componente
    │   - A transição entre seções é fluida: o gesto não interrompe ao
    │     cruzar a divisória, independente de a seção de destino estar vazia
    │
    ├─ Tap em componente ativo → abre modal de opções
    │   - Configurações de layout e conteúdo do componente
    │   - Ativação/desativação não acontece na modal; ocorre por drag entre seções
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
  GetDashboardPreferencesUseCase      ← mapeia null → defaults; expõe Flow<List<...>>
  BuildDashboardViewingUseCase        ← constrói lista de DashboardComponentVariant a partir de prefs + dados

Data (database/)
  DashboardPreferencesRepository      ← persiste em Settings (JSON)

UI (screen/dashboard/)
  DashboardComponentType              ← enum com key + defaults inerentes ao componente
  DashboardPreviewFactory             ← gera DashboardComponentVariant.Preview com dados mock para edit mode
  DashboardComponentsBuilder          ← constrói DashboardComponent com dados reais e config
  DashboardUiState (Loading | Empty | Viewing | Editing) ← estados como tipos distintos
  DashboardAction                     ← ações de edição
  DashboardViewModel                  ← orquestra preferências + dados reais
  DashboardScreen                     ← renderiza normal e edit mode
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
    fun observe(): StateFlow<List<DashboardComponentPreference>?>
    suspend fun save(preferences: List<DashboardComponentPreference>)
}
```

Sem `currentPreferences()` síncrono. O próprio repositório expõe um `StateFlow` já carregado e com semântica explícita:
- `null` = ainda não existe preferência salva (primeira abertura)
- `emptyList()` = o usuário removeu todos os componentes e confirmou a edição

> Ver issues/issues-step4.md — Issues 1 e 2 (motivação da semântica null vs emptyList e dos configs padrão).

---

## 6. Data Layer

### 6.1 `DashboardPreferencesRepository`

```kotlin
// database/repository/DashboardPreferencesRepository.kt
class DashboardPreferencesRepository(
    private val settings: Settings,
) : IDashboardPreferencesRepository {

    private val _preferences = MutableStateFlow(load())

    override fun observe(): StateFlow<List<DashboardComponentPreference>?> = _preferences

    override suspend fun save(preferences: List<DashboardComponentPreference>) {
        val json = Json.encodeToString(preferences.map { it.toSerializable() })
        settings.putString(KEY, json)
        _preferences.value = preferences
    }

    private fun load(): List<DashboardComponentPreference>? {
        val json = settings.getStringOrNull(KEY) ?: return null
        return runCatching {
            Json.decodeFromString<List<SerializablePreference>>(json)
                .map { it.toDomain() }
        }.getOrNull()
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

Essa distinção entre `null` e lista vazia evita ambiguidade entre "primeira abertura" e "dashboard vazia salva pelo usuário".

---

## 7. UI Layer

### 7.1 `DashboardComponentType`

Enum que centraliza a chave e os defaults inerentes a cada componente. Substitui o `DashboardComponentRegistry` previsto anteriormente. A composição inicial da dashboard fica em `GetDashboardPreferencesUseCase.defaultPreferences()`, enquanto `DashboardComponentType.defaultConfig` representa apenas o que é próprio do componente e deve valer também quando ele for adicionado depois.

```kotlin
// ui/screen/dashboard/DashboardComponentType.kt
enum class DashboardComponentType(
    val key: String,
    val defaultConfig: Map<String, String> = emptyMap(),
) {
    TOTAL_BALANCE(key = "total_balance"),
    CONCRETE_BALANCE_STATS(key = "balance_stats_concrete"),
    PENDING_BALANCE_STATS(
        key = "balance_stats_pending",
        defaultConfig = mapOf(DashboardComponentConfig.HIDE_WHEN_EMPTY to "true"),
    ),
    CREDIT_CARD_BALANCE_STATS(
        key = "balance_stats_credit_card",
        defaultConfig = mapOf(DashboardComponentConfig.HIDE_WHEN_EMPTY to "true"),
    ),
    ACCOUNTS_OVERVIEW(
        key = "accounts_overview",
        defaultConfig = mapOf(AccountsOverviewConfig.HIDE_SINGLE_ACCOUNT to "true"),
    ),
    CREDIT_CARDS_PAGER(key = "credit_cards_pager"),
    SPENDING_BY_CATEGORY(key = "spending_by_category"),
    INCOME_BY_CATEGORY(key = "income_by_category"),
    BUDGETS(key = "budgets"),
    PENDING_RECURRING(key = "pending_recurring"),
    RECENTS(key = "recents"),
    QUICK_ACTIONS(
        key = "quick_actions",
        defaultConfig = mapOf(DashboardComponentConfig.SHOW_HEADER to "false"),
    );

    companion object {
        fun fromKey(key: String): DashboardComponentType? = entries.find { it.key == key }
    }
}
```

Configuração inicial da dashboard padrão:

```kotlin
private fun defaultPreferences(): List<DashboardComponentPreference> = listOf(
    DashboardComponentPreference(
        key = DashboardComponentType.TOTAL_BALANCE.key,
        position = 0,
        config = emptyMap(),
    ),
    DashboardComponentPreference(
        key = DashboardComponentType.CONCRETE_BALANCE_STATS.key,
        position = 1,
        config = emptyMap(),
    ),
    DashboardComponentPreference(
        key = DashboardComponentType.PENDING_BALANCE_STATS.key,
        position = 2,
        config = mapOf(DashboardComponentConfig.HIDE_WHEN_EMPTY to "true"),
    ),
    DashboardComponentPreference(
        key = DashboardComponentType.ACCOUNTS_OVERVIEW.key,
        position = 3,
        config = mapOf(
            DashboardComponentConfig.TOP_SPACING to "true",
            AccountsOverviewConfig.HIDE_SINGLE_ACCOUNT to "true",
        ),
    ),
    DashboardComponentPreference(
        key = DashboardComponentType.CREDIT_CARDS_PAGER.key,
        position = 4,
        config = mapOf(DashboardComponentConfig.TOP_SPACING to "true"),
    ),
    DashboardComponentPreference(
        key = DashboardComponentType.SPENDING_BY_CATEGORY.key,
        position = 5,
        config = mapOf(DashboardComponentConfig.TOP_SPACING to "true"),
    ),
    DashboardComponentPreference(
        key = DashboardComponentType.BUDGETS.key,
        position = 6,
        config = mapOf(DashboardComponentConfig.TOP_SPACING to "true"),
    ),
    DashboardComponentPreference(
        key = DashboardComponentType.PENDING_RECURRING.key,
        position = 7,
        config = mapOf(DashboardComponentConfig.TOP_SPACING to "true"),
    ),
    DashboardComponentPreference(
        key = DashboardComponentType.RECENTS.key,
        position = 8,
        config = mapOf(DashboardComponentConfig.TOP_SPACING to "true"),
    ),
    DashboardComponentPreference(
        key = DashboardComponentType.QUICK_ACTIONS.key,
        position = 9,
        config = mapOf(
            DashboardComponentConfig.TOP_SPACING to "true",
            DashboardComponentConfig.SHOW_HEADER to "false",
        ),
    ),
)
```

Exemplo de consequência prática:
- `Recents` começa com `top_spacing = true` na dashboard padrão
- `Recents` adicionado depois, a partir de `availableItems`, não herda esse `top_spacing` automaticamente

### 7.2 `DashboardPreviewFactory`

Classe separada responsável por criar instâncias de `DashboardComponentVariant.Preview` com dados mock realistas para cada componente. É registrada como `single` no Koin e injetada no `DashboardViewModel`.

```kotlin
// ui/screen/dashboard/DashboardPreviewFactory.kt
class DashboardPreviewFactory {
    suspend fun createPreview(key: String): DashboardComponentVariant? = when (key) {
        DashboardComponentType.TOTAL_BALANCE.key ->
            DashboardComponentVariant.TotalBalance.Preview(
                component = DashboardComponent.TotalBalance(amount = 5432.10),
            )
        // ... demais tipos com dados mock realistas ...
        else -> null
    }
}
```

O método é `suspend` porque usa `getString()` de resources para os textos dos mocks (nomes de conta, categoria, etc.). É chamado exclusivamente dentro de `viewModelScope.launch` no `buildEditingState`.

### 7.3 `DashboardUiState` — sealed class

O modo de visualização e o modo de edição são tipos distintos do UiState, seguindo o mesmo padrão de `BudgetsUiState`, `AccountsUiState` e demais screens do projeto.

```kotlin
// ui/screen/dashboard/DashboardUiState.kt
sealed class DashboardUiState {
    abstract val yearMonth: YearMonth

    data class Loading(
        override val yearMonth: YearMonth = Clock.System.now().toYearMonth(),
    ) : DashboardUiState()

    data class Empty(
        override val yearMonth: YearMonth,
        val accounts: List<Account> = emptyList(),
        val creditCards: List<CreditCard> = emptyList(),
    ) : DashboardUiState()

    data class Viewing(
        override val yearMonth: YearMonth,
        val items: List<DashboardComponentVariant>,  // variantes com dados reais + config
        val accounts: List<Account> = emptyList(),
        val creditCards: List<CreditCard> = emptyList(),
    ) : DashboardUiState()

    data class Editing(
        override val yearMonth: YearMonth,
        val activeItems: List<DashboardEditItem>,    // componentes atualmente na dashboard
        val availableItems: List<DashboardEditItem>, // disponíveis para adicionar
        val accounts: List<Account> = emptyList(),   // passados para configs de AccountsOverview
        val creditCards: List<CreditCard> = emptyList(), // passados para configs de CreditCardsPager
    ) : DashboardUiState()
}

data class DashboardEditItem(
    val preview: DashboardComponentVariant,  // variante Preview — dados mock + config padrão
    val config: Map<String, String> = emptyMap(),
) {
    val key: String get() = preview.key
    val title: UiText get() = preview.title
}
```

- `Loading` — carregamento inicial antes dos repositórios emitirem
- `Empty` — dashboard sem componentes visíveis, com affordance explícita para abrir edição (ver issues/issues.md — Issue 1)
- `Viewing` — modo normal; `items` contém `DashboardComponentVariant.Viewing` com dados ao vivo
- `Editing` — modo edição; `activeItems` contém `DashboardComponentVariant.Preview` com dados mock

`accounts` e `creditCards` estão presentes em `Empty`, `Viewing` e `Editing` para que o `enterEditMode` os repasse ao `buildEditingState` sem nova consulta ao repositório.

`yearMonth` é `abstract` pois aparece no seletor de mês em ambos os modos visíveis.

### 7.4 `DashboardAction` — expansão

```kotlin
// ui/screen/dashboard/DashboardAction.kt
sealed class DashboardAction {
    data object EnterEditMode : DashboardAction()
    data object ConfirmEdit : DashboardAction()
    data object CancelEdit : DashboardAction()
    data class MoveComponent(val fromKey: String, val toKey: String) : DashboardAction()
    data class UpdateComponentConfig(val key: String, val config: Map<String, String>) : DashboardAction()
}
```

`MoveComponent(fromKey, toKey)` usa chaves string estáveis em vez de índices inteiros. O ViewModel determina o tipo de movimento pelo contexto: se `toKey == EDIT_SECTION_HEADER_KEY` ou `EDIT_AVAILABLE_PLACEHOLDER_KEY`, é cruzamento de fronteira; caso contrário, é reordenação interna ou cruzamento via componente adjacente.

### 7.5 `DashboardViewModel` — novas responsabilidades

`editingState` é um `MutableStateFlow<DashboardUiState.Editing?>` separado que, quando não-nulo, tem prioridade sobre o estado reativo dos repositórios. Isso congela a UI durante a edição sem cancelar os flows de dados. As preferências da dashboard usam semântica tri-state: `null` para primeira abertura, lista preenchida para composição salva, `emptyList()` para dashboard vazia salva.

```kotlin
class DashboardViewModel(
    // ... repositórios existentes ...
    private val getDashboardPreferences: GetDashboardPreferencesUseCase,
    private val buildDashboardViewingUseCase: BuildDashboardViewingUseCase,
    private val dashboardPreferencesRepository: IDashboardPreferencesRepository,
    private val dashboardPreviewFactory: DashboardPreviewFactory,
    // DashboardComponentsBuilder é detalhe interno de BuildDashboardViewingUseCase — não injetado aqui
) : ViewModel() {

    // StateFlow<List<...>> — nunca nulo; null do repositório já mapeado para defaults pelo UseCase
    private val preferences = getDashboardPreferences()
        .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList())

    // Editing state — quando não-nulo, sobrescreve o Viewing reativo
    private val editingState = MutableStateFlow<DashboardUiState.Editing?>(null)

    // Flow reativo que sempre produz Loading → Empty/Viewing
    private val viewingState: Flow<DashboardUiState> = combine(
        preferences,
        // ... demais flows de repositórios (operações, cartões, contas, orçamentos, recorrentes) ...
    ) { preferences, /* ... */ ->
        val items = buildDashboardViewingUseCase(input = dashboardInput, preferences = preferences)
        if (items.isEmpty()) {
            DashboardUiState.Empty(yearMonth = targetMonth, accounts = accounts, creditCards = creditCards)
        } else {
            DashboardUiState.Viewing(yearMonth = targetMonth, items = items, accounts = accounts, creditCards = creditCards)
        }
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        editingState,
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
        is MoveComponent         -> moveComponent(action.fromKey, action.toKey)
        is UpdateComponentConfig -> updateComponentConfig(action.key, action.config)
    }

    private fun enterEditMode() {
        val current = uiState.value
        viewModelScope.launch {
            when (current) {
                is DashboardUiState.Viewing ->
                    openEditingState(current.yearMonth, current.accounts, current.creditCards)
                is DashboardUiState.Empty ->
                    openEditingState(current.yearMonth, current.accounts, current.creditCards)
                else -> Unit
            }
        }
    }

    private suspend fun openEditingState(
        yearMonth: YearMonth,
        accounts: List<Account>,
        creditCards: List<CreditCard>,
    ) {
        editingState.value = buildEditingState(
            yearMonth = yearMonth,
            accounts = accounts,
            creditCards = creditCards,
            preferences = preferences.value,
        )
    }

    // cancelEdit NÃO re-salva no repositório — o repositório não foi modificado durante o edit mode.
    // Basta limpar editingState e o viewingState reativo volta a comandar com as prefs anteriores.
    private fun cancelEdit() {
        editingState.value = null
    }

    private fun confirmEdit() {
        viewModelScope.launch {
            val editing = editingState.value ?: return@launch
            val prefs = editing.activeItems.mapIndexed { i, item ->
                DashboardComponentPreference(key = item.key, position = i, config = item.config)
            }
            dashboardPreferencesRepository.save(prefs)
            editingState.value = null
        }
    }

    // moveComponent opera sobre a lista combinada (activeItems + availableItems) usando chaves.
    // Quando toKey == "section_header", "active_placeholder" ou "available_placeholder": cruzamento de fronteira
    //   (fromInActive) → desativa: item vai para o início dos disponíveis (activeCount - 1)
    //   (!fromInActive) → ativa: item vai para o final dos ativos (activeCount + 1)
    // Caso contrário: reordenação interna ou cruzamento via componente adjacente
    //   from e to determinam newActiveCount comparando seus índices com activeCount
    // Ver lógica completa na seção 10.5 da spec.
    private fun moveComponent(fromKey: String, toKey: String) { /* ver seção 10.5 */ }

    private fun updateComponentConfig(key: String, config: Map<String, String>) {
        val current = editingState.value ?: return
        editingState.value = current.copy(
            activeItems = current.activeItems.map { if (it.key == key) it.copy(config = config) else it },
        )
    }

    // Regra de visibilidade em edit mode:
    //   activeItems    = componentes nas prefs salvas (já não-nulas graças ao UseCase)
    //   availableItems = componentes do enum DashboardComponentType ausentes das prefs
    //
    // "Sem dados no modo visualização" ≠ "removido pelo usuário":
    //   - CreditCardsPager sem cartões cadastrados → aparece em activeItems (adicionado, mas sem dados)
    //   - CreditCardsPager removido pelo usuário   → aparece em availableItems
    //
    // A fonte de verdade são as preferências — nunca o viewingState,
    // que está filtrado por dados e não reflete a intenção do usuário.
    // Ver issues/issues-step1.md — Issue 3.
    private suspend fun buildEditingState(
        yearMonth: YearMonth,
        accounts: List<Account>,
        creditCards: List<CreditCard>,
        preferences: List<DashboardComponentPreference>,
    ): DashboardUiState.Editing {
        val activeItems = preferences.sortedBy { it.position }.mapNotNull { pref ->
            val preview = dashboardPreviewFactory.createPreview(pref.key) ?: return@mapNotNull null
            DashboardEditItem(preview = preview, config = pref.config)
        }

        val presentKeys = preferences.map { it.key }.toSet()
        val availableItems = DashboardComponentType.entries
            .filter { it.key !in presentKeys }
            .mapNotNull { entry ->
                val preview = dashboardPreviewFactory.createPreview(entry.key) ?: return@mapNotNull null
                DashboardEditItem(preview = preview, config = entry.defaultConfig)
            }

        return DashboardUiState.Editing(
            yearMonth = yearMonth,
            activeItems = activeItems,
            availableItems = availableItems,
            accounts = accounts,
            creditCards = creditCards,
        )
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
- **Long press + drag** no componente → reordena (o componente inteiro é arrastável — o ícone de handle é um hint visual, não o único ponto de arraste)
- **Tap** no componente → abre modal de opções (excluir, configurações futuras)
- **Sem botão de excluir** — a exclusão fica na modal de opções

**Anti-padrão explicitamente proibido — drag acionado apenas pelo ícone de handle:**
```
┌─────────────────────────────┐   ← ERRADO: só o ☰ é arrastável; componente não renderizado
│ ☰  Saldo Total          [−] │
│ ☰  Cartões de Crédito   [−] │
│ ☰  Contas               [−] │
└─────────────────────────────┘
```

**Padrão correto:**
```
╔═════════════════════════════╗   ← borda sutil indica modo editável (ex: outline)
║  Saldo Total           ☰   ║   ← cabeçalho: título + ícone DragHandle (arrastável sem long press)
╠═════════════════════════════╣
║                             ║
║   R$ 5.450,00               ║   ← TotalBalance renderizado com mock data
║                             ║
╚═════════════════════════════╝
  ↑ tap → abre modal de opções | long press em qualquer ponto → arrasta para reordenar

╔═════════════════════════════╗
║  Cartões de Crédito    ☰   ║
╠═════════════════════════════╣
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
// Usar Transition.Crossfade com contentKey = { it::class } — NÃO o Crossfade standalone.
// O Crossfade standalone compara identidade de objeto: cada MoveComponent gera um novo
// DashboardUiState.Editing, o que destrói e recria o composable (incluindo lazyListState
// e reorderState) no meio de um drag. Ver issues/issues-step1.md — Issue 2.
val transition = updateTransition(targetState = uiState)
transition.Crossfade(contentKey = { it::class }) { state ->
    when (state) {
        is DashboardUiState.Loading -> DashboardLoadingContent()
        is DashboardUiState.Empty -> DashboardEmptyContent(onAction)
        is DashboardUiState.Viewing -> DashboardViewingContent(state, onAction)
        is DashboardUiState.Editing -> DashboardEditingContent(state, onAction)
    }
}
```

Como `DashboardViewingContent` e `DashboardEditingContent` renderizam os mesmos componentes na mesma ordem e com dimensões idênticas, o `Crossfade` cria o efeito visual de affordances aparecendo/desaparecendo **sobre** os componentes em seus lugares.

---

### 8.2 `DashboardEditingContent` — estrutura

Lista unificada: itens ativos e disponíveis convivem no **mesmo bloco `items()`** do `LazyColumn`, divididos por um cabeçalho de seção não-arrastável.

**Requisito crítico:** todos os itens devem ser renderizados por um único `items()` call — nunca em blocos separados (`items(state.activeItems)` + `item()` + `items(state.availableItems)`). Com blocos separados, quando um item cruza a fronteira entre seções o seu `ReorderableItem` é desmontado em um bloco e remontado em outro, interrompendo o gesto de drag em andamento.

A solução é uma lista combinada com um sealed interface `EditListEntry` (`Component | SectionHeader | AvailablePlaceholder`). O `SectionHeader` e o `AvailablePlaceholder` são envolvidos em `ReorderableItem` **sem `draggableHandle`** — visíveis como destinos de drop mas não arrastáveis. Ver seção 10.3 para o racional completo.

**`onMove` baseado em chave:** o callback usa `from.key` e `to.key` (strings estáveis) em vez de `from.index`/`to.index`. O ViewModel interpreta as chaves para determinar o tipo de movimento (intra-seção, cruzamento via componente, cruzamento via cabeçalho). Ver seção 10.4–10.5.

---

### 8.3 `DashboardEditItemWrapper` — renderização fiel ao original

O componente inteiro é draggable e tappable (sem botão de excluir visível). Dois pontos de arraste coexistem:

- **Corpo do componente:** `longPressDraggableHandle()` aplicado em um overlay `Box` que cobre o wrapper inteiro — inicia drag somente após long press, preservando o tap para abrir a modal.
- **Ícone `DragHandle`:** `draggableHandle()` aplicado diretamente no ícone — inicia drag imediatamente ao toque, sem long press. O ícone é posicionado como último filho do `Box` raiz, ficando acima do overlay em Z-order e interceptando toques em sua área antes do overlay.

```kotlin
// longPressDraggableHandle e draggableHandle são extensions de ReorderableCollectionItemScope.
// O scope deve ser passado explicitamente ao extrair este composable para fora do lambda de ReorderableItem.
@Composable
fun ReorderableCollectionItemScope.DashboardEditItemWrapper(
    item: DashboardEditItem,
    onTap: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        color = colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                // Cabeçalho: título + espaço reservado para o ícone DragHandle
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = stringUiText(item.title), style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.size(20.dp)) // reserva espaço do ícone
                }
                // Preview do componente com dados mock — mesmo composable do Viewing
                DashboardComponentContent(variant = item.preview, modifier = Modifier.fillMaxWidth())
            }

            // Overlay global: intercepta tap e long press + drag no corpo do wrapper.
            // IMPORTANTE: usar longPressDraggableHandle (não draggableHandle) pois o item também é clicável.
            // draggableHandle() iniciaria o drag imediatamente ao toque, bloqueando os cliques.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(onClick = onTap)
                    .longPressDraggableHandle(
                        onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                        onDragStopped = { haptic.performHapticFeedback(HapticFeedbackType.GestureEnd) },
                    )
            )

            // Ícone DragHandle acima do overlay (Z-order): arraste imediato sem long press.
            // Posicionado como último filho do Box para ficar na frente do overlay na ordem de renderização.
            // Toques na área do ícone são consumidos aqui e não chegam ao overlay abaixo.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(20.dp)
                        .draggableHandle(
                            onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                            onDragStopped = { haptic.performHapticFeedback(HapticFeedbackType.GestureEnd) },
                        ),
                )
            }
        }
    }
}
```

`DashboardComponentContent` é o mesmo switch `when(component)` que já existe no `DashboardViewingContent` — extraído para uma função compartilhada no mesmo arquivo ou em `DashboardComponents.kt`:

```kotlin
// Assinatura — when(variant) compartilhado entre Viewing e Editing
@Composable
fun DashboardComponentContent(
    variant: DashboardComponentVariant,
    modifier: Modifier = Modifier,
) {
    when (variant) {
        is DashboardComponentVariant.TotalBalance        -> TotalBalanceCard(variant, modifier)
        is DashboardComponentVariant.ConcreteBalanceStats -> DashboardConcreteBalanceSection(variant, modifier)
        // ... demais tipos
    }
}
```

Em `DashboardViewingContent`, cada componente é convertido para sua variante `Viewing` via `component.toViewingVariant(...)` antes de chamar `DashboardComponentContent`.
Em `DashboardEditItemWrapper`, `item.preview` já é uma `DashboardComponentVariant.XxxType.Preview` — chamado diretamente sem callbacks de interação (componente frozen).

**Resolução de conflito de gestos:** `clickable` dispara no tap-up sem movimento; `longPressDraggableHandle` só ativa após long press + movimento. Eles coexistem naturalmente no overlay. O ícone `DragHandle` fica acima do overlay em Z-order, consumindo toques em sua área antes que cheguem ao overlay — nessa área o drag inicia imediatamente, sem long press e sem acionar o tap do overlay.

---

### 8.3.1 `DashboardComponentOptionsModal`

Modal de opções acionada pelo tap em um componente ativo no edit mode. Implementada como `ModalBottomSheet` do `ModalManager`.

```
╔══════════════════════════════╗
║  Saldo Total                 ║  ← título do componente (item.title)
╠══════════════════════════════╣
║  Layout                      ║  ← seção universal
║  - Mostrar cabeçalho*        ║
║  - Espaçamento superior      ║
║                              ║
║  Conteúdo*                   ║  ← configs específicas do componente
╠══════════════════════════════╣
║  [Cancelar]   [Confirmar]    ║  ← botões de ação
╚══════════════════════════════╝

* quando aplicável ao componente
```

As alterações feitas pelo usuário são mantidas em estado local (`var config`). A propagação para o ViewModel ocorre **apenas ao confirmar**: o botão "Confirmar" chama `onAction(DashboardAction.UpdateComponentConfig(...))` e fecha o modal via `manager.dismiss()`. O botão "Cancelar" apenas fecha sem persistir.

```kotlin
class DashboardComponentOptionsModal(
    private val item: DashboardEditItem,
    private val accounts: List<Account>,
    private val creditCards: List<CreditCard>,
    private val onAction: (DashboardAction) -> Unit,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        var config by remember { mutableStateOf(item.config) }
        val modalManager = LocalModalManager.current

        fun updateConfig(newConfig: Map<String, String>) {
            config = newConfig
        }

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(stringUiText(item.title), style = MaterialTheme.typography.titleLarge)

            // Seção Layout — presente em todos os componentes
            DashboardConfigSectionLabel(stringResource(Res.string.component_config_layout_section))

            // SHOW_HEADER — para: AccountsOverview, CreditCardsPager, PendingRecurring, Recents, QuickActions
            // TOP_SPACING — para todos os componentes

            // Seção Conteúdo — presente apenas nos componentes com configs específicas
            when (item.key) {
                // ver seção 11 da spec
            }

            HorizontalDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { modalManager.dismiss() }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.component_config_cancel))
                }
                Button(
                    onClick = {
                        onAction(DashboardAction.UpdateComponentConfig(item.key, config))
                        modalManager.dismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(Res.string.component_config_confirm))
                }
            }
        }
    }
}
```

Instanciação no call site (em `DashboardEditingContent`):

```kotlin
modalManager.show(
    DashboardComponentOptionsModal(
        item = entry.item,
        accounts = state.accounts,
        creditCards = state.creditCards,
        onAction = onAction,
    )
)
```

`accounts` e `creditCards` são passados diretamente do `DashboardUiState.Editing` para que os configs de `AccountsOverview` e `CreditCardsPager` possam listar as entidades reais como toggles.

O modal atual é exclusivamente configuracional. Ativação e desativação de componentes acontecem na própria lista unificada, por drag entre as seções ativa e disponível.

---

### 8.4 `DashboardEditItemWrapper` — variante inativa

Itens da seção "Disponíveis para adicionar" usam o mesmo `DashboardEditItemWrapper`, mas com `modifier = Modifier.alpha(0.6f)` aplicado externamente no call site (no `DashboardEditingContent`). O callback `onTap` é passado vazio para itens inativos — a verificação de `isActive` ocorre no call site antes de mostrar a modal.

```kotlin
// DashboardEditingContent — call site
DashboardEditItemWrapper(
    item = entry.item,
    onTap = {
        if (entry.isActive) {
            modalManager.show(
                DashboardComponentOptionsModal(
                    item = entry.item,
                    accounts = state.accounts,
                    creditCards = state.creditCards,
                    onAction = onAction,
                )
            )
        }
    },
    modifier = Modifier.alpha(if (entry.isActive) 1f else 0.6f),
)
```

Itens inativos não abrem modal ao tap — o único meio de ativação é arrastar para a seção ativa.

---

### 8.5 Transição Normal ↔ Edit Mode — detalhes

**Chrome (toolbar + bottom nav + FAB):**
- TopAppBar: `AnimatedContent(targetState = isEditMode)` — a toolbar normal e a de edição fazem crossfade
- BottomNavigationBar: `AnimatedVisibility(visible = !isEditMode, enter = slideInVertically { it }, exit = slideOutVertically { it })`
- FAB: some em edit mode (comportamento atual mantido) — não há painel a abrir

**Por componente:**
Não há affordances visuais explícitas (sem drag handle, sem botão remover, sem borda). O overlay translúcido sobre o componente é o único indicador de que está em modo editável. O `Crossfade` global cria a transição natural de visualização → edição.

**Ativação por long press em qualquer componente:**
```kotlin
// Aplicado no wrapper de cada componente no DashboardViewingContent.
// interceptLongPress é uma extension de Modifier em /extension/Modifier.kt que usa
// PointerEventPass.Initial para interceptar o gesto antes dos filhos — necessário porque
// componentes com clickable/combinedClickable consomem o evento no Main pass, impedindo
// que um detector externo acumule o threshold de long press.
// Também cancela corretamente ao detectar scroll (touchSlop) ou evento consumido pelo filho,
// e consome os eventos restantes após disparar para evitar que a ação do filho também execute.
// Ver issues/issues-step1.md — Issues 1, 4 e 5.
Modifier.interceptLongPress { onAction(DashboardAction.EnterEditMode) }
```

**Detecção no HomeScreen:**

O `HomeScreen` já instancia o `DashboardViewModel` (ou pode acessá-lo via Koin `koinViewModel()`). Basta observar o `uiState` coletado como state — comportamento atual mantido:

```kotlin
// HomeScreen.kt
val dashboardViewModel: DashboardViewModel = koinViewModel()
val dashboardUiState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
val isEditMode = dashboardUiState is DashboardUiState.Editing

// Bottom nav — some em edit mode
AnimatedVisibility(
    visible = !isEditMode,
    enter = slideInVertically { it },
    exit = slideOutVertically { it },
) {
    BottomNavigationBar(...)
}

// FAB — some em edit mode (não há painel a abrir)
AnimatedVisibility(visible = !isEditMode) {
    FloatingActionButton(onClick = { modalManager.show(AddTransactionModal()) }) {
        Icon(Icons.Default.Add, contentDescription = null)
    }
}
```

O `DashboardViewModel` é `single` no escopo do `NavBackStackEntry` do route `Home`, portanto a mesma instância é compartilhada entre `HomeScreen` e `DashboardScreen`.

---

### 8.6 Edit Toolbar

```
[ Cancelar ]   ────── Editar ──────   [ Confirmar ]
                                                       [FAB +/×] ← bottom-right (FabPosition.End)
```

Substituí a TopAppBar normal via `AnimatedContent(targetState = isEditMode)`. Ambas as versões têm a mesma altura para evitar layout shift durante a transição.

O botão de abrir/fechar o `AddComponentPanel` é o FAB reposicionado — não há elemento adicional na toolbar de edição.

---

## 9. Lista unificada — seções Ativa e Disponível

Não existe `AddComponentPanel`. A lista de edição é única e contém duas seções separadas por um cabeçalho fixo sempre visível:

```
╔══════════════════════════════╗
║   R$ 5.450,00                ║  ← ativo — DashboardEditItemWrapper (isActive=true)
╚══════════════════════════════╝
╔══════════════════════════════╗
║  ┌──────┐ ┌──────┐           ║  ← ativo
╚══════════════════════════════╝

──── Disponíveis para adicionar ────  ← cabeçalho fixo — SEMPRE VISÍVEL

╔══════════════════════════════╗  ← disponível — DashboardEditItemWrapper (isActive=false)
║  [componente dimmed]         ║     overlay mais opaco, sem tap para modal
╚══════════════════════════════╝
```

Quando `availableItems` estiver vazio, o cabeçalho permanece visível e um placeholder é exibido no lugar dos itens disponíveis:

```
──── Disponíveis para adicionar ────  ← sempre visível

  ┌ - - - - - - - - - - - - - - ┐
  |  ↓  Arraste aqui para       |  ← placeholder com borda tracejada
  |     ocultar um componente   |
  └ - - - - - - - - - - - - - - ┘
```

**Regras de ativação/desativação:**
- Arrastar qualquer item **para acima do cabeçalho** → ativa (entra em `items`)
- Arrastar qualquer item **para abaixo do cabeçalho** → desativa (entra em `availableItems`)
- O cabeçalho em si é o ponto de cruzamento — mover um item "sobre" o cabeçalho é suficiente para mudar de seção
- **Sem tap para ativar/desativar** — drag é o único meio de transição entre seções

**Requisito crítico de fluência:**
O gesto de drag **nunca deve ser interrompido** ao cruzar a divisória, independentemente de qual seção estiver vazia. O usuário deve conseguir arrastar livremente de qualquer posição da seção ativa para qualquer posição da seção disponível (e vice-versa) em um único gesto contínuo.

---

## 10. Drag & Drop

### 10.1 Biblioteca e versão

**Biblioteca:** `sh.calvin.reorderable:reorderable` (Compose Multiplatform compatível)

```kotlin
// build.gradle.kts (composeApp)
implementation("sh.calvin.reorderable:reorderable:3.0.0")
```

### 10.2 Estrutura de dados da lista (`EditListEntry`)

A lista unificada é representada por um sealed interface `EditListEntry` que combina todos os itens em um único `items()` call:

```kotlin
sealed interface EditListEntry {
    val key: String

    data class Component(val item: DashboardEditItem, val isActive: Boolean) : EditListEntry {
        override val key = item.key
    }
    data object ActivePlaceholder : EditListEntry {
        override val key = EDIT_ACTIVE_PLACEHOLDER_KEY
    }
    data object SectionHeader : EditListEntry {
        override val key = EDIT_SECTION_HEADER_KEY
    }
    data object AvailablePlaceholder : EditListEntry {
        override val key = EDIT_AVAILABLE_PLACEHOLDER_KEY
    }
}

// Constantes definidas em DashboardEditListEntries.kt
const val EDIT_SECTION_HEADER_KEY = "section_header"
const val EDIT_ACTIVE_PLACEHOLDER_KEY = "active_placeholder"
const val EDIT_AVAILABLE_PLACEHOLDER_KEY = "available_placeholder"
```

A lista é construída como:

```kotlin
@Composable
fun rememberDashboardEditListEntries(state: DashboardUiState.Editing): List<EditListEntry> =
    remember(state.activeItems, state.availableItems) {
        buildList {
            if (state.activeItems.isEmpty()) {
                add(EditListEntry.ActivePlaceholder)
            } else {
                state.activeItems.forEach { add(EditListEntry.Component(it, isActive = true)) }
            }
            add(EditListEntry.SectionHeader)
            if (state.availableItems.isEmpty()) {
                add(EditListEntry.AvailablePlaceholder)
            } else {
                state.availableItems.forEach { add(EditListEntry.Component(it, isActive = false)) }
            }
        }
}
```

**Requisito crítico:** todos os itens renderizados pelo `LazyColumn` devem pertencer ao **mesmo e único `items()` call**. Jamais usar blocos separados (`items(state.activeItems)` + `item()` + `items(state.availableItems)`). Com blocos separados, quando um item cruza a fronteira entre seções o seu `ReorderableItem` é desmontado em um bloco e remontado em outro, interrompendo o gesto de drag em andamento.

### 10.3 `SectionHeader` dentro de `ReorderableItem` sem handle

**Crítico para fluência do drag:** o `SectionHeader` deve ser envolvido em `ReorderableItem(reorderState, key = "section_header")` — sem `draggableHandle`.

```kotlin
EditListEntry.SectionHeader -> {
    ReorderableItem(reorderState, key = "section_header") {
        Text(
            text = stringResource(Res.string.dashboard_edit_available_section),
            ...
        )
    }
}
```

**Por que isso é necessário:**

A biblioteca `sh.calvin.reorderable` funciona no modelo swap-adjacente: o `onMove` dispara um passo por vez (`from → from±1`). Quando um item chega ao vizinho do cabeçalho, o próximo `onMove` precisa "passar pelo" cabeçalho para chegar ao outro lado.

Se o `SectionHeader` estiver **fora** de `ReorderableItem`:
- A biblioteca o ignora como destino de drop
- Quando a seção de destino está vazia, nenhum `onMove` dispara → impossível cruzar a fronteira

Se o `SectionHeader` estiver **dentro** de `ReorderableItem` sem handle:
- A biblioteca o enxerga como destino válido (`to`)
- Dispara `onMove(último_ativo, section_header)` ou `onMove(primeiro_inativo, section_header)`
- O ViewModel interpreta `toKey == "section_header"` como inserção de fronteira
- Sem handle → o cabeçalho não pode ser arrastado (`from`), apenas recebido (`to`)

**Atenção:** `enabled = false` NÃO deve ser usado. Embora pareça a solução para "não arrastável mas destino válido", na prática a biblioteca exclui itens com `enabled = false` completamente do sistema de drop — eles não disparam `onMove` nem como `to`. O mecanismo correto para "destino mas não origem" é `ReorderableItem` sem `draggableHandle`. _(ver issues/issues-step3.md — Issue 2)_

O `AvailablePlaceholder` e o `ActivePlaceholder` seguem a mesma regra — envolvidos em `ReorderableItem` sem handle, servindo como destinos de drop quando suas respectivas seções estão vazias. O `onMove` ignora todos os placeholders e o cabeçalho como origem:

```kotlin
) { from, to ->
    val fromKey = when (from.key) {
        EditListEntry.ActivePlaceholder.key,
        EditListEntry.SectionHeader.key,
        EditListEntry.AvailablePlaceholder.key -> return@rememberReorderableLazyListState
        else -> from.key.toString()
    }
    onAction(DashboardAction.MoveComponent(fromKey = fromKey, toKey = to.key.toString()))
    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
}
```

### 10.4 `MoveComponent` baseado em chave (não em índice)

`DashboardAction.MoveComponent` usa `fromKey: String` e `toKey: String` em vez de índices inteiros:

```kotlin
data class MoveComponent(val fromKey: String, val toKey: String) : DashboardAction()
```

O callback `onMove` extrai as chaves:

```kotlin
val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
    val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
    val toKey = to.key as? String ?: return@rememberReorderableLazyListState
    onAction(DashboardAction.MoveComponent(fromKey, toKey))
    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
}
```

**Por que chaves em vez de índices:**

O `onMove` pode ser chamado com o estado de índices desatualizado (stale) quando há re-composição concorrente. Chaves são estáveis independente de reordenações — `fromKey` e `toKey` identificam os itens sem ambiguidade mesmo após mudanças no tamanho da lista.

### 10.5 Lógica de `moveComponent` no ViewModel

```kotlin
private fun moveComponent(fromKey: String, toKey: String) {
    val current = editingState.value ?: return

    val allItems = current.activeItems + current.availableItems
    val fromIndex = allItems.indexOfFirst { it.key == fromKey }.takeIf { it >= 0 } ?: return

    val activeCount = current.activeItems.size

    when (toKey) {
        EDIT_ACTIVE_PLACEHOLDER_KEY -> {
            // Ativa o item quando a seção ativa está vazia (só funciona se activeCount == 0)
            if (activeCount != 0) return
            val mutable = allItems.toMutableList()
            val moved = mutable.removeAt(fromIndex)
            mutable.add(0, moved)
            editingState.value = current.copy(
                items = mutable.take(1),
                availableItems = mutable.drop(1),
            )
        }

        EDIT_SECTION_HEADER_KEY, EDIT_AVAILABLE_PLACEHOLDER_KEY -> {
            // Cruzamento de fronteira: inserção na borda da seção de destino
            val fromInActive = fromIndex < activeCount
            val mutable = allItems.toMutableList()
            val moved = mutable.removeAt(fromIndex)
            if (fromInActive) {
                // Ativo → inativo: insere no início dos disponíveis
                val newActiveCount = activeCount - 1
                mutable.add(newActiveCount, moved)
                editingState.value = current.copy(
                    items = mutable.take(newActiveCount),
                    availableItems = mutable.drop(newActiveCount),
                )
            } else {
                // Inativo → ativo: insere no final dos ativos
                mutable.add(activeCount, moved)
                val newActiveCount = activeCount + 1
                editingState.value = current.copy(
                    items = mutable.take(newActiveCount),
                    availableItems = mutable.drop(newActiveCount),
                )
            }
        }

        else -> {
            // Reordenação dentro da mesma seção ou cruzamento via componente adjacente
            val toIndex = allItems.indexOfFirst { it.key == toKey }.takeIf { it >= 0 } ?: return
            val fromInActive = fromIndex < activeCount
            val toInActive = toIndex < activeCount

            val mutable = allItems.toMutableList()
            val moved = mutable.removeAt(fromIndex)
            mutable.add(toIndex.coerceAtMost(mutable.size), moved)

            val newActiveCount = when {
                fromInActive && !toInActive -> activeCount - 1
                !fromInActive && toInActive -> activeCount + 1
                else -> activeCount
            }
            editingState.value = current.copy(
                items = mutable.take(newActiveCount),
                availableItems = mutable.drop(newActiveCount),
            )
        }
    }
}
```

### 10.6 Armadilhas conhecidas

| Sintoma | Causa raiz | Solução |
|---------|-----------|---------|
| Drag cancela ao cruzar a divisória | `SectionHeader` fora de `ReorderableItem` — biblioteca pula o header, índice esperado diverge do real | Envolver `SectionHeader` em `ReorderableItem` sem `draggableHandle` (não usar `enabled=false`) |
| Impossível desativar quando seção disponível está vazia | Sem destino de drop abaixo do cabeçalho | Exibir `AvailablePlaceholder` em `ReorderableItem` sem `draggableHandle` quando `availableItems` vazio |
| Impossível ativar quando seção ativa está vazia | Sem destino de drop acima do cabeçalho | Exibir `ActivePlaceholder` em `ReorderableItem` sem `draggableHandle` quando `items` vazio |
| Item vai para posição errada ao cruzar seção | Inserção na posição do item de destino em vez da borda da seção | Quando `toKey == EDIT_SECTION_HEADER_KEY`, usar inserção de fronteira no ViewModel |
| `IndexOutOfBoundsException` no `add` | Remoção de elemento reduz o tamanho antes do `add` | Separar `removeAt` e `add`; usar `.coerceAtMost(mutable.size)` |
| Drag correto mas com índices stale | Usar `from.index`/`to.index` após re-composição | Usar `from.key`/`to.key` (estável) em vez de índices |
| Item inativo aparece na seção errada | Dois blocos `items()` separados destroem e recriam `ReorderableItem` ao cruzar seção | Único bloco `items(listEntries)` com `EditListEntry` sealed |

---

## 11. Configurações de Componentes

Cada componente pode ter configurações próprias acessíveis pelo tap em edit mode (`DashboardComponentOptionsModal`). As configurações são persistidas em `DashboardComponentPreference.config` como `Map<String, String>`.

Os componentes sem configurações específicas exibem apenas a configuração universal de layout.

---

### 11.0 Configurações Universais de Layout

Disponíveis para **todos** os 12 componentes. Exibidas na seção "Layout" da modal.

| Config | Chave | Tipo | Default | Opções |
|--------|-------|------|---------|--------|
| Espaçamento superior extra | `top_spacing` | `"true"` / `"false"` | `"false"` | toggle |
| Exibir cabeçalho | `show_header` | `"true"` / `"false"` | `"true"` | toggle (apenas em alguns) |

**`top_spacing`:** quando `"true"`, adiciona um `Spacer(Modifier.height(16.dp))` acima do componente no `DashboardViewingContent`. O espaço é parte do wrapper — não afeta o preview em edit mode.

**`show_header`:** controla a visibilidade do cabeçalho do componente. Disponível apenas para: `AccountsOverview`, `CreditCardsPager`, `PendingRecurring`, `Recents`, `QuickActions`.

```kotlin
object DashboardComponentConfig {
    const val TOP_SPACING = "top_spacing"
    const val SHOW_HEADER = "show_header"
    const val SHOW_EMPTY_STATE = "show_empty_state"
    const val HIDE_WHEN_EMPTY = "hide_when_empty"
}
```

**Leitura no `DashboardViewingContent`:**
```kotlin
// No wrapper de cada componente no LazyColumn — config vem da DashboardComponentVariant.config
val topSpacing = config[DashboardComponentConfig.TOP_SPACING] == "true"
if (topSpacing) Spacer(Modifier.height(16.dp))
DashboardComponentContent(variant = variant, modifier = Modifier.fillMaxWidth())
```

`DashboardComponentsBuilder` ignora `top_spacing` e `show_header` ao construir dados — eles são exclusivos da camada de renderização.

---

### 11.1 TotalBalance

Sem configurações de conteúdo. Sempre exibe o saldo consolidado de todas as contas.

---

### 11.2 ConcreteBalanceStats

| Config | Chave | Tipo | Default | Opções |
|--------|-------|------|---------|--------|
| Ocultar sem dados | `hide_when_empty` | `"true"` / `"false"` | `"false"` | toggle |

**Impacto no builder:** quando `"true"` e receitas = 0 e despesas = 0, o builder retorna `null` e o componente não é exibido no modo visualização.

---

### 11.3 PendingBalanceStats

| Config | Chave | Tipo | Default | Opções |
|--------|-------|------|---------|--------|
| Ocultar sem dados | `hide_when_empty` | `"true"` / `"false"` | `"true"` | toggle |

Default `"true"` — o componente é oculto automaticamente quando não há recorrentes pendentes.

---

### 11.3b CreditCardBalanceStats

| Config | Chave | Tipo | Default | Opções |
|--------|-------|------|---------|--------|
| Ocultar sem dados | `hide_when_empty` | `"true"` / `"false"` | `"true"` | toggle |

Default `"true"` — oculto automaticamente quando não há pagamentos ou despesas de cartão no mês.

---

### 11.4 AccountsOverview

| Config | Chave | Tipo | Default | Opções |
|--------|-------|------|---------|--------|
| Ocultar quando há apenas uma conta | `hide_single_account` | `"true"` / `"false"` | `"true"` | toggle |
| Contas excluídas da visão | `excluded_account_ids` | IDs separados por vírgula | `""` (todas) | Seleção múltipla de contas |

**Na modal:** toggle "Ocultar conta única" + lista de contas com toggle para incluir/excluir cada uma.

**Impacto no builder:** filtra contas excluídas; quando `hide_single_account == "true"` e só resta uma conta após o filtro, o componente não é exibido.

```kotlin
object AccountsOverviewConfig {
    const val HIDE_SINGLE_ACCOUNT = "hide_single_account"
    const val EXCLUDED_ACCOUNT_IDS = "excluded_account_ids"
}
```

---

### 11.5 CreditCardsPager

| Config | Chave | Tipo | Default | Opções |
|--------|-------|------|---------|--------|
| Exibir empty state | `show_empty_state` | `"true"` / `"false"` | `"false"` | toggle |
| Cartões excluídos da visão | `excluded_card_ids` | IDs separados por vírgula | `""` (todos) | Seleção múltipla de cartões |

**Na modal:** toggle "Exibir empty state" + lista de cartões com toggle para incluir/excluir cada um.

**Impacto no builder:** quando não há cartões cadastrados (ou todos excluídos) e `show_empty_state == "false"`, o componente não é exibido; quando `"true"`, exibe `CreditCardsPager.Empty`.

```kotlin
object CreditCardsPagerConfig {
    const val EXCLUDED_CARD_IDS = "excluded_card_ids"
}
```

`SHOW_EMPTY_STATE` usa a chave de `DashboardComponentConfig.SHOW_EMPTY_STATE`.

---

### 11.6 SpendingByCategory

| Config | Chave | Tipo | Default | Opções |
|--------|-------|------|---------|--------|
| Máximo de categorias exibidas | `max_categories` | Int como string | `"-1"` (todas) | 3, 5, 10, todas |

**Na modal:** seleção do limite com segmented button.

**Impacto no builder:** aplica `.take(maxCategories)` na lista de `CategorySpending`. `-1` = sem limite.

```kotlin
object SpendingByCategoryConfig {
    const val MAX_CATEGORIES = "max_categories"
    const val ALL = "-1"
}
```

---

### 11.6b IncomeByCategory

| Config | Chave | Tipo | Default | Opções |
|--------|-------|------|---------|--------|
| Máximo de categorias exibidas | `max_categories` | Int como string | `"-1"` (todas) | 3, 5, 10, todas |

Idêntico ao `SpendingByCategory`, mas para receitas.

```kotlin
object IncomeByCategoryConfig {
    const val MAX_CATEGORIES = "max_categories"
    const val ALL = "-1"
}
```

---

### 11.6c Budgets

Sem configurações de conteúdo. Exibe todos os orçamentos com progresso de gastos.

---

### 11.7 PendingRecurring

| Config | Chave | Tipo | Default | Opções |
|--------|-------|------|---------|--------|
| Dias à frente | `upcoming_days_ahead` | Int como string | `"0"` | 0 (hoje), 7, 15, 30 |

> Ver issues/issues-step4.md — Issue 3

**Na modal:** segmented button com opções: Hoje / 7 dias / 15 dias / Este mês.

**Impacto no builder:** além das recorrentes já vencidas (pendentes), inclui as que vencem nos próximos `upcoming_days_ahead` dias. `0` = apenas as pendentes do dia atual.

```kotlin
object PendingRecurringConfig {
    const val UPCOMING_DAYS_AHEAD = "upcoming_days_ahead"
    const val DEFAULT_UPCOMING_DAYS_AHEAD = 0
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
| Primeira abertura | `observe()` retorna `null`; `GetDashboardPreferencesUseCase` mapeia para a composição inicial explícita da dashboard |
| Preferências salvas | Aplica ordem e config salvas; componentes ausentes das prefs aparecem em `availableItems` no edit mode |
| Dashboard esvaziada pelo usuário | Persiste `[]`; `GetDashboardPreferencesUseCase` emite `[]` (não mapeia lista vazia para defaults) |
| Novo componente adicionado ao enum (futuro) | Aparece em `availableItems` no edit mode por ser ausente das prefs salvas |
| Componente removido do enum (futuro) | Ignorado silenciosamente ao carregar prefs (`mapNotNull { createPreview(it.key) }`) |

---

## 13. DI

```kotlin
// RepositoryModule.kt
single<IDashboardPreferencesRepository> {
    DashboardPreferencesRepository(settings = get())
}

// ViewModelModule.kt
single { GetDashboardPreferencesUseCase(get()) }       // single — StateFlow compartilhado
factory { BuildDashboardViewingUseCase(get()) }        // factory — sem estado
single { DashboardPreviewFactory() }                   // single — sem estado, mas suspend (resources)

viewModel {
    DashboardViewModel(
        // ... repositórios existentes ...
        getDashboardPreferences = get(),
        buildDashboardViewingUseCase = get(),
        dashboardPreferencesRepository = get(),
        dashboardPreviewFactory = get(),
    )
}
```

---

## 14. Strings necessárias (`strings.xml`)

```xml
<!-- Nomes dos componentes -->
<string name="component_total_balance">Saldo Total</string>
<string name="component_balance_stats">Balanço</string>
<string name="component_pending_balance">Balanço Pendente</string>
<string name="component_credit_card_balance_stats">Balanço do Cartão</string>
<string name="component_accounts_overview">Contas</string>
<string name="component_credit_cards">Cartões de Crédito</string>
<string name="component_spending_by_category">Gastos por Categoria</string>
<string name="component_income_by_category">Receitas por Categoria</string>
<string name="component_budgets">Orçamentos</string>
<string name="component_pending_recurring">Recorrências</string>
<string name="component_recents">Recentes</string>
<string name="component_quick_actions">Atalhos</string>

<!-- Edit mode toolbar -->
<string name="dashboard_edit_title">Editar</string>
<string name="dashboard_edit_confirm">Confirmar</string>
<string name="dashboard_edit_cancel">Cancelar</string>

<!-- Placeholders da lista de edição -->
<string name="dashboard_edit_active_placeholder">Arraste aqui para adicionar um componente</string>
<string name="dashboard_edit_available_section">Disponíveis para adicionar</string>
<string name="dashboard_edit_available_placeholder">Arraste aqui para ocultar um componente</string>

<!-- Seções da modal de configuração -->
<string name="component_config_layout_section">Layout</string>
<string name="component_config_content_section">Conteúdo</string>

<!-- Configs universais -->
<string name="component_config_show_header">Exibir cabeçalho</string>
<string name="component_config_top_spacing">Espaçamento superior</string>

<!-- Configs de conteúdo -->
<string name="component_config_hide_single_account">Ocultar conta única</string>
<string name="component_config_hide_when_empty">Ocultar sem dados</string>
<string name="component_config_show_empty_state">Exibir empty state</string>
<string name="component_config_max_categories">Máximo de categorias</string>
<string name="component_config_days_ahead">Dias à frente</string>
<string name="component_config_today">Hoje</string>
<string name="component_config_7_days">7 dias</string>
<string name="component_config_15_days">15 dias</string>
<string name="component_config_this_month">Este mês</string>
<string name="component_config_count">Quantidade de itens</string>
<string name="component_config_all">Todas</string>
<string name="component_config_min_visible_action">Mantenha pelo menos uma ação visível</string>

<!-- Dados de preview (mock) — usados pelo DashboardPreviewFactory -->
<string name="preview_account_main">Carteira</string>
<string name="preview_account_savings">Poupança</string>
<string name="preview_card_nubank">Nubank</string>
<string name="preview_category_food">Alimentação</string>
<string name="preview_category_transport">Transporte</string>
<string name="preview_category_salary">Salário</string>
<string name="preview_category_freelance">Freelance</string>
<string name="preview_transaction_supermarket">Supermercado</string>
<string name="preview_transaction_netflix">Netflix</string>
<string name="preview_transaction_spotify">Spotify</string>
<string name="preview_budget_food">Alimentação</string>
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
| `DashboardUiState` como sealed class (`Loading`, `Empty`, `Viewing`, `Editing`) | `data class` com `editState: EditState?` | Loading, dashboard vazia, visualização e edição são estados distintos da tela; modelá-los separadamente elimina estados impossíveis e segue o padrão do projeto |
| `editingState: MutableStateFlow<Editing?>` separado do combine reativo | Unificar tudo em um único combine | Separa responsabilidades: dados ao vivo ficam no `viewingState`, edição em curso fica no `editingState` — evita reconstrução do estado de edição a cada emissão dos repositórios |
| `DashboardComponentContent` extraído como função compartilhada | Duplicar o `when(component)` em Viewing e Editing | Garante que edit mode renderiza EXATAMENTE o mesmo composable que o modo normal — sem risco de divergência visual |
| `Crossfade` no nível da tela para a transição de modo | `AnimatedContent` com slides | Como ambos os modos renderizam os mesmos componentes nas mesmas posições, o crossfade cria a ilusão de affordances aparecendo in-place sem custo de implementação de shared elements |
| Componentes em edit mode: composable original com mock data + overlay | Card simplificado com título | Critério de aceite — o modo edição deve remeter ao modo visualização; lista genérica com títulos é explicitamente reprovada |
| Long press no corpo + ícone `DragHandle` arrastável sem long press | Ícone `DragHandle` como único ponto de drag, ou drag no corpo sem long press | O ícone é uma affordance visual explícita para usuários que não descobrem o long press; o corpo permite arraste com long press para usuários que já sabem; o drag no ícone sem long press reduz a fricção para quem prefere a affordance visual |
| Tap no componente → modal de opções de configuração | Botão "−" visível no componente | Preferência do produto: controles explícitos de exclusão poluem o visual; a modal centraliza configurações, enquanto ativação/desativação fica no drag entre seções |
| `AddComponentPanel` como overlay in-tree | `ModalBottomSheet` do `ModalManager` | Drag cross-container requer espaço de coordenadas compartilhado |
| `russhwolf/settings` + JSON para persistência | Room (nova tabela) | Sem relações, sem queries — settings é suficiente e já disponível |
| `sh.calvin.reorderable` para drag in-list | `detectDragGesturesAfterLongPress` manual | API de alto nível, multiplatform, menos boilerplate |
| Preferências como lista de chaves visíveis | `(key, visible: Boolean)` | Lista menor, semântica mais clara, componentes novos aparecem automaticamente |
