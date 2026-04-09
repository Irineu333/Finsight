# Plano: Dashboard Customizável — Modo Edição

Evolução registrada em 5 etapas independentes e validáveis.
A spec completa está em [`spec.md`](spec.md).

> Este documento registra a evolução da implementação. Quando houver conflito entre uma etapa intermediária e o comportamento final, prevalecem a `spec.md`, o código atual e as correções registradas em `issues/`.
>
> Observação importante: a ação explícita de `"Remover"` na modal existiu nas etapas iniciais, mas foi substituída no fluxo final pelo modelo de seções ativa/disponível. Hoje a ativação e a desativação acontecem por drag entre seções; a modal ficou dedicada a configurações.

---

## Etapa 1 — Modo edição com drag and drop em lista simples (Implementado)

**Foco:** estrutura arquitetural completa + drag and drop funcionando.
Os componentes são representados por cards simplificados (título + placeholder) nesta etapa — a UI fiel ao original vem na Etapa 2.

### O que entra

**Domain:**
- `DashboardComponentPreference(key, position, config)` — model de preferência
- `IDashboardPreferencesRepository` — interface com `observe()` e `save()`

**Data:**
- `DashboardPreferencesRepository` — persiste em `Settings` + JSON

**UI:**
- `DashboardComponentRegistry` — registro inicial dos componentes com título e posição default (substituído depois por `DashboardComponentType`)
- `DashboardUiState` selada: `Loading`, `Empty`, `Viewing`, `Editing`
- `DashboardAction` expandida: `EnterEditMode`, `ConfirmEdit`, `CancelEdit`, `MoveComponent`, `RemoveComponent`
- `DashboardViewModel` — lógica de edit mode com `editingState` separado do combine reativo
- `DashboardScreen` — `Crossfade` entre `Loading`, `Empty`, `Viewing`, `Editing`
- `DashboardEditingContent` — `LazyColumn` com `sh.calvin.reorderable`, cards simplificados
  - Em Etapa 1, `DashboardEditItemWrapper` renderiza apenas `item.title` em um card simples — `item.preview` existe no model mas é ignorado até Etapa 2
- `DashboardComponentOptionsModal` — modal inicial com apenas "Remover" (substituída depois pela modal de configurações)
- Edit toolbar: `Cancelar | [título do modo edição] | Confirmar`
- Ocultar `BottomNavigationBar` em edit mode
- Long press em qualquer componente no `DashboardViewingContent` → `EnterEditMode`
- `DashboardPreferencesRepository` no DI

**Dependência nova:**
```kotlin
implementation("sh.calvin.reorderable:reorderable:3.0.0")
```

### O que NÃO entra
- Componentes reais no edit mode (cards simplificados são suficientes)
- `DashboardComponentMocks`
- `AddComponentPanel`
- `AddComponent` action
- Configurações de componentes

### Critérios de aceite
- [x] Long press em qualquer componente entra no modo edição com transição suave
- [x] Bottom nav desaparece ao entrar em edit mode
- [x] Toolbar de edição aparece (`Cancelar | [título] | Confirmar`)
- [x] Componentes são arrastáveis por long press + drag no componente inteiro (ícone de handle é puramente visual)
- [x] A ordem dos componentes muda visualmente durante o drag
- [x] Na etapa inicial, tap em um componente abria uma modal simples com "Remover" (fluxo depois substituído pelo modelo ativo/disponível)
- [x] "Confirmar" persiste a nova ordem e composição — ao reabrir o app a ordem é mantida
- [x] "Cancelar" descarta as alterações e restaura o estado anterior
- [x] Todos os componentes adicionados aparecem em edit mode, mesmo que sem dados no modo visualização
- [x] Componentes explicitamente removidos pelo usuário não aparecem em edit mode (ficam em availableItems)

> **Reprovação imediata:** reordenação por botões ↑↓ ou qualquer controle que não seja drag físico.

> **Issues:** [issues/issues-step1.md](issues/issues-step1.md)

---

## Etapa 2 — Componentes reais no modo edição

**Foco:** UI fiel ao original — o modo edição deve remeter visualmente ao modo visualização.

### Pré-requisito
Etapa 1 aprovada.

### O que entra

- `DashboardComponentContent` — função compartilhada extraída do `DashboardViewingContent`
  (mesmo `when(component)` usado nos dois modos, sem duplicação)
- `DashboardComponentMocks` — instâncias estáticas com dados de exemplo realistas para cada componente
- `DashboardEditItem.preview: DashboardComponent` — referência ao mock do componente
- `DashboardEditItemWrapper` — substitui o card simplificado da Etapa 1:
  - Renderiza `DashboardComponentContent(item.preview)` — composable original com mock data
  - Componente é frozen (consume todos os eventos de pointer)
  - Borda sutil (`outlineVariant`) indica estado editável
  - Overlay translúcido mínimo sobre o componente
  - `draggableHandle()` no wrapper inteiro
  - `clickable` abre `DashboardComponentOptionsModal`
  - `shadow` elevado durante drag (`isDragging`)
- Ajuste fino na transição `Crossfade` (timing, spring stiffness)

### O que NÃO entra
- `AddComponentPanel`
- Configurações de componentes

### Critérios de aceite
- [x] Cada componente em edit mode parece o componente real com dados de exemplo
- [x] Nenhum componente é representado apenas por seu título/nome
- [x] Componentes não respondem a interações próprias em edit mode (frozen)
- [x] O drag continua funcionando corretamente com os componentes reais renderizados

> **Reprovação imediata:** qualquer componente que em edit mode exiba apenas texto/título em vez do visual original.

> **Issues:** [issues/issues.md](issues/issues.md)

---

## Etapa 3 — Adicionar componentes (lista unificada)

**Foco:** lista única com seções Ativa e Disponível — drag intra-lista com transição fluida entre seções.

### Pré-requisito
Etapa 2 aprovada.

### O que entra

**`DashboardAction.MoveComponent`** — mudança de índices para chaves:
- `MoveComponent(val from: Int, val to: Int)` → `MoveComponent(val fromKey: String, val toKey: String)`

**`DashboardViewModel.moveComponent(fromKey, toKey)`** — lógica baseada em chave:
- `toKey == "section_header"` ou `"available_placeholder"` → cruzamento de fronteira (inserção na borda da seção de destino)
- Caso contrário → reordenação interna ou cruzamento via componente adjacente (determina newActiveCount comparando índices com activeCount)
- Ver lógica completa na spec seção 10.5

**`DashboardEditingContent`** — `LazyColumn` com lista unificada `EditListEntry`:
- Sealed interface `EditListEntry`: `Component(item, isActive)` | `SectionHeader` | `AvailablePlaceholder`
- Único `items(listEntries, key = { it.entryKey })` call — nunca blocos separados
- `SectionHeader` envolvido em `ReorderableItem(reorderState, key = "section_header")` — sem `draggableHandle`, mas ainda destino válido de drop
- `AvailablePlaceholder` envolvido em `ReorderableItem(reorderState, key = "available_placeholder")` — sem `draggableHandle`, exibido quando `availableItems` vazio
- Cabeçalho **sempre visível** (não some quando seção disponível vazia)
- `onMove` usa `from.key as? String` / `to.key as? String`

**`DashboardEditItemWrapper`** — parâmetro `isActive: Boolean` adicionado:
- `isActive = true`: overlay 10%, tap abre modal de opções
- `isActive = false`: overlay ~35%, sem tap para modal

**`DashboardAvailablePlaceholder`** — placeholder com borda tracejada + ícone + texto, exibido quando `availableItems` vazio

### O que NÃO entra
- Configurações de componentes
- `AddComponentPanel`, `DragToAddState`, drag cross-container

### Critérios de aceite
- [x] Cabeçalho "Disponíveis para adicionar" é **sempre visível** em edit mode
- [x] Quando todos os componentes estão ativos, placeholder com borda tracejada aparece abaixo do cabeçalho
- [x] Componentes disponíveis têm visual diferenciado (overlay mais escuro)
- [x] Long press + drag de um componente ativo **para abaixo do cabeçalho** → componente é desativado
- [x] Long press + drag de um componente inativo **para acima do cabeçalho** → componente é ativado
- [x] O drag **não interrompe** ao cruzar a divisória — o gesto continua em um único movimento contínuo
- [x] Consegue desativar um componente mesmo sem outros componentes inativos
- [x] Consegue ativar um componente mesmo sem outros componentes ativos
- [x] A reordenação dentro da seção ativa continua funcionando normalmente
- [x] "Confirmar" persiste a nova composição; componentes ativados aparecem no modo visualização
- [x] "Cancelar" descarta ativações/desativações e restaura o estado anterior

> **Issues:** [issues/issues-step3.md](issues/issues-step3.md)

---

## Etapa 4 — Configurações de componentes (Implementado)

**Foco:** modal de configuração por componente, com persistência dos configs.

### Pré-requisito
Etapa 3 aprovada.

### O que entra

**Domain:**
- `DashboardComponentPreference.config: Map<String, String>` — já no model desde Etapa 1, mas não usado
- Config constants:
  - `DashboardComponentConfig.TOP_SPACING` — universal, todos os componentes
  - `AccountsOverviewConfig.EXCLUDED_ACCOUNT_IDS`
  - `CreditCardsPagerConfig.EXCLUDED_CARD_IDS`
  - `SpendingByCategoryConfig.MAX_CATEGORIES`
  - `IncomeByCategoryConfig.MAX_CATEGORIES`
  - `PendingRecurringConfig.UPCOMING_DAYS_AHEAD`
  - `RecentsConfig.COUNT`
  - `QuickActionsConfig.HIDDEN_ACTIONS`

**Data:**
- `DashboardPreferencesRepository.save()` já persiste `config` (campo já existe no model)

**UI:**
- `DashboardAction.UpdateComponentConfig(key, config)` já declarado no sealed class desde Etapa 1 — Etapa 4 implementa o handler no ViewModel e os controles na modal
- `DashboardComponentOptionsModal` — expandida com:
  - Toggle "Espaçamento superior" presente em **todos** os componentes (universal)
  - Ação de remover deixa de ser o fluxo principal; ativação/desativação passa a ocorrer por drag entre as seções ativa e disponível
  - Configurações específicas por componente abaixo:
    - AccountsOverview: lista de contas com toggle
    - CreditCardsPager: lista de cartões com toggle
    - SpendingByCategory: segmented button (3 / 5 / 10 / Todas)
    - IncomeByCategory: segmented button (3 / 5 / 10 / Todas)
    - PendingRecurring: segmented button (Hoje / 7 dias / 15 dias / Este mês)
    - Recents: segmented button (4 / 6 / 8 / 10)
    - QuickActions: lista de 7 ações com toggle (mínimo 1 visível)
- `DashboardViewingContent` — lê `top_spacing` do config e insere `Spacer(16.dp)` acima do componente quando habilitado
- `DashboardComponentsBuilder` — lê configs específicos ao construir cada componente (ignora `top_spacing`, que é renderização pura)

### Critérios de aceite
- [x] Toggle "Espaçamento superior" aparece na modal de todos os componentes
- [x] Ativar espaçamento superior adiciona espaço visível acima do componente ao confirmar o edit mode
- [x] Desativar remove o espaçamento
- [x] Espaçamento persiste entre sessões do app
- [x] Tap em um componente com configuração específica disponível exibe as opções na modal
- [x] QuickActions: desativar uma ação a remove do componente na dashboard
- [x] QuickActions: não é possível desativar todas as ações (validação na modal)
- [x] Recents: alterar o número de itens reflete imediatamente ao confirmar o edit mode
- [x] PendingRecurring: alterar o horizonte futuro reflete ao confirmar
- [x] SpendingByCategory: alterar o limite de categorias reflete ao confirmar
- [x] IncomeByCategory: alterar o limite de categorias reflete ao confirmar
- [x] AccountsOverview: excluir uma conta a remove do componente
- [x] CreditCardsPager: excluir um cartão o remove do componente
- [x] Todas as configurações persistem entre sessões do app

> **Issues:** [issues/issues-step4.md](issues/issues-step4.md)

---

## Etapa 5 — Melhorias e Refatoração (Implementado)

Melhorias arquiteturais aplicadas após a conclusão da Etapa 4, consolidando decisões que evoluíram durante a implementação:

**Novos componentes:**
- `CreditCardBalanceStats` (`balance_stats_credit_card`) — pagamentos e gastos com cartão no mês
- `IncomeByCategory` (`income_by_category`) — receitas por categoria
- `Budgets` (`budgets`) — progresso de orçamentos
- `SpendingPager` foi separado em `SpendingByCategory` + `Budgets` (componentes independentes)

**Refatorações estruturais:**
- **`DashboardComponentRegistry` eliminado:** substituído por `DashboardComponentType` enum — título migrou para `DashboardComponentVariant.title`, defaults para `GetDashboardPreferencesUseCase`
- **`GetDashboardPreferencesUseCase` extraído:** lógica `null → defaults` saiu do ViewModel; o ViewModel recebe sempre `List<DashboardComponentPreference>` não-nula via `stateIn(Eagerly)`
- **Configuração inicial da dashboard separada dos defaults do componente:** `GetDashboardPreferencesUseCase.defaultPreferences()` passou a declarar explicitamente a composição inicial; `DashboardComponentType.defaultConfig` ficou restrito a defaults inerentes ao componente, que também devem valer quando ele é adicionado depois
- **`BuildDashboardViewingUseCase` extraído:** constrói `List<DashboardComponentVariant>` a partir de prefs + dados reais; separa responsabilidade do ViewModel
- **`DashboardComponentMocks` eliminado → `DashboardPreviewFactory`:** classe separada injetável via Koin; `suspend` pois usa `getString()` de resources
- **`DashboardUiState.Viewing.items`** agora é `List<DashboardComponentVariant>` em vez de `List<DashboardComponent>` — unifica o contrato de `DashboardComponentContent` entre Viewing e Editing
- **`DashboardUiState.Empty`/`Viewing`/`Editing`** recebem `accounts` e `creditCards` — necessário para popular os configs de `AccountsOverview` e `CreditCardsPager` na modal sem nova consulta ao repositório
- **`DashboardEditItem`** simplificado: campos `key` e `title` removidos (derivados de `preview.key` e `preview.title`)
- **`DashboardUiState.Editing.items` → `activeItems`:** o contrato do estado passou a nomear explicitamente a seção superior do edit mode como a lista de componentes ativos
- **`DashboardAction.RemoveComponent` removido:** o fluxo morto foi eliminado depois da consolidação do UX final, que usa apenas drag entre as seções ativa e disponível para ativar/desativar componentes

**Melhorias no edit mode:**
- **`ActivePlaceholder` adicionado ao `EditListEntry`:** resolve o drop quando a seção ativa está completamente vazia; tratado como caso `EDIT_ACTIVE_PLACEHOLDER_KEY` no `moveComponent`
- **`DashboardEditPlaceholder`** compartilhado entre `ActivePlaceholder` e `AvailablePlaceholder` (antes havia só `DashboardAvailablePlaceholder`)
- **`interceptLongPress`** substituiu `combinedClickable(onLongClick)` na detecção do long press em `DashboardViewingContent`
- **`DashboardEditItemWrapper` redesenhado:** adicionado cabeçalho com título do componente + ícone `DragHandle` acima do preview; diferenciação ativo/inativo trocada de overlay colorido para `alpha` (1f ativo, 0.6f inativo) aplicado externamente no `DashboardEditingContent`
- **Ícone `DragHandle` arrastável sem long press:** o ícone é posicionado como último filho do `Box` raiz (acima do overlay em Z-order) com `draggableHandle()` — arraste inicia imediatamente ao toque no ícone. O overlay mantém `longPressDraggableHandle()` para o restante do corpo.
- **`DashboardComponentOptionsModal`** recebe `accounts` e `creditCards` explicitamente (necessário para as configs adicionadas na Etapa 4)
- **Modal organizada em seções:** "Layout" (universal) e "Conteúdo" (específica por componente)
- **Botões de confirmação e cancelamento na modal de configuração:** alterações são mantidas em estado local durante a edição; `onAction(UpdateComponentConfig)` é disparado apenas ao confirmar. Cancelar fecha o modal sem persistir.
- **Botões de ação em massa no cabeçalho da lista de edição:** ícones `ArrowUpward` (adicionar tudo) e `ArrowDownward` (remover tudo) posicionados à direita do título "Disponíveis para adicionar"; cada um visível apenas quando sua operação é aplicável. Disparam `DashboardAction.AddAllComponents` / `RemoveAllComponents`, processados no ViewModel via funções `addAllComponents()` / `removeAllComponents()`.

**Configs adicionadas além do planejado em Etapa 4:**
- `SHOW_HEADER` — visibilidade do cabeçalho (AccountsOverview, CreditCardsPager, PendingRecurring, Recents, QuickActions)
- `HIDE_WHEN_EMPTY` — para ConcreteBalanceStats, PendingBalanceStats e CreditCardBalanceStats
- `SHOW_EMPTY_STATE` — para CreditCardsPager
- `HIDE_SINGLE_ACCOUNT` — para AccountsOverview

**Renomeações de config keys:**
- `SpendingPagerConfig.MAX_CATEGORIES` → `SpendingByCategoryConfig.MAX_CATEGORIES` + `IncomeByCategoryConfig.MAX_CATEGORIES`
- `PendingRecurringConfig.DAYS_AHEAD` → `PendingRecurringConfig.UPCOMING_DAYS_AHEAD` (default 0, não 30; opções: 0/7/15/30)
