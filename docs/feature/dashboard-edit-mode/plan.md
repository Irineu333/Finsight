# Plano: Dashboard Customizável — Modo Edição

Implementação dividida em 4 etapas independentes e validáveis.
A spec completa está em [`spec.md`](spec.md).

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
- `DashboardComponentRegistry` — registro dos 9 componentes com título e posição default
- `DashboardUiState` selada: `Loading`, `Viewing`, `Editing`
- `DashboardAction` expandida: `EnterEditMode`, `ConfirmEdit`, `CancelEdit`, `MoveComponent`, `RemoveComponent`
- `DashboardViewModel` — lógica de edit mode com `_editingState` separado do combine reativo
- `DashboardScreen` — `Crossfade` entre `Loading`, `Viewing`, `Editing`
- `DashboardEditingContent` — `LazyColumn` com `sh.calvin.reorderable`, cards simplificados
  - Em Etapa 1, `DashboardEditItemWrapper` renderiza apenas `item.title` em um card simples — `item.preview` existe no model mas é ignorado até Etapa 2
- `DashboardComponentOptionsModal` — modal com apenas "Remover" (sem settings ainda)
- Edit toolbar: `Cancelar | Editar | Confirmar`
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
- [x] Toolbar de edição aparece (Cancelar | Editar | Confirmar)
- [x] Componentes são arrastáveis por long press + drag (o componente inteiro, sem ícone de handle)
- [x] A ordem dos componentes muda visualmente durante o drag
- [x] Tap em um componente abre a modal de opções com "Remover" ()
- [x] "Remover" remove o componente da lista com animação de saída
- [x] "Confirmar" persiste a nova ordem e composição — ao reabrir o app a ordem é mantida
- [x] "Cancelar" descarta as alterações e restaura o estado anterior
- [x] Todos os componentes adicionados aparecem em edit mode, mesmo que sem dados no modo visualização
- [x] Componentes explicitamente removidos pelo usuário não aparecem em edit mode (ficam em availableItems)

> **Reprovação imediata:** reordenação por botões ↑↓ ou qualquer controle que não seja drag físico.

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
- `SectionHeader` envolvido em `ReorderableItem(reorderState, key = "section_header", enabled = false)` — **obrigatório para fluência**
- `AvailablePlaceholder` envolvido em `ReorderableItem(reorderState, key = "available_placeholder", enabled = false)` — exibido quando `availableItems` vazio
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
- [ ] Cabeçalho "Disponíveis para adicionar" é **sempre visível** em edit mode
- [ ] Quando todos os componentes estão ativos, placeholder com borda tracejada aparece abaixo do cabeçalho
- [ ] Componentes disponíveis têm visual diferenciado (overlay mais escuro)
- [ ] Long press + drag de um componente ativo **para abaixo do cabeçalho** → componente é desativado
- [ ] Long press + drag de um componente inativo **para acima do cabeçalho** → componente é ativado
- [ ] O drag **não interrompe** ao cruzar a divisória — o gesto continua em um único movimento contínuo
- [ ] Consegue desativar o último componente inativo arrastando para a área do placeholder
- [ ] Consegue ativar um componente mesmo sem outros componentes inativos (seção inativa vazia)
- [ ] A reordenação dentro da seção ativa continua funcionando normalmente
- [ ] "Confirmar" persiste a nova composição; componentes ativados aparecem no modo visualização
- [ ] "Cancelar" descarta ativações/desativações e restaura o estado anterior

---

## Etapa 4 — Configurações de componentes

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
  - `SpendingPagerConfig.MAX_CATEGORIES`
  - `PendingRecurringConfig.DAYS_AHEAD`
  - `RecentsConfig.COUNT`
  - `QuickActionsConfig.HIDDEN_ACTIONS`

**Data:**
- `DashboardPreferencesRepository.save()` já persiste `config` (campo já existe no model)

**UI:**
- `DashboardAction.UpdateComponentConfig(key, config)` já declarado no sealed class desde Etapa 1 — Etapa 4 implementa o handler no ViewModel e os controles na modal
- `DashboardComponentOptionsModal` — expandida com:
  - Toggle "Espaçamento superior" presente em **todos** os componentes (universal)
  - Configurações específicas por componente abaixo:
    - AccountsOverview: lista de contas com toggle
    - CreditCardsPager: lista de cartões com toggle
    - SpendingPager: segmented button (3 / 5 / 10 / Todas)
    - PendingRecurring: segmented button (7 / 14 / 30 dias)
    - Recents: segmented button (4 / 6 / 8 / 10)
    - QuickActions: lista de 7 ações com toggle (mínimo 1 visível)
- `DashboardViewingContent` — lê `top_spacing` do config e insere `Spacer(16.dp)` acima do componente quando habilitado
- `DashboardComponentsBuilder` — lê configs específicos ao construir cada componente (ignora `top_spacing`, que é renderização pura)

### Critérios de aceite
- [ ] Toggle "Espaçamento superior" aparece na modal de todos os componentes
- [ ] Ativar espaçamento superior adiciona espaço visível acima do componente ao confirmar o edit mode
- [ ] Desativar remove o espaçamento
- [ ] Espaçamento persiste entre sessões do app
- [ ] Tap em um componente com configuração específica disponível exibe as opções na modal
- [ ] QuickActions: desativar uma ação a remove do componente na dashboard
- [ ] QuickActions: não é possível desativar todas as ações (validação na modal)
- [ ] Recents: alterar o número de itens reflete imediatamente ao confirmar o edit mode
- [ ] PendingRecurring: alterar o horizonte de dias reflete ao confirmar
- [ ] SpendingPager: alterar o limite de categorias reflete ao confirmar
- [ ] AccountsOverview: excluir uma conta a remove do componente
- [ ] CreditCardsPager: excluir um cartão o remove do componente
- [ ] Todas as configurações persistem entre sessões do app