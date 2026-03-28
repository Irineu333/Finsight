# Plano: Dashboard Customizável — Modo Edição

Implementação dividida em 4 etapas independentes e validáveis.
A spec completa está em [`spec.md`](spec.md).

---

## Etapa 1 — Modo edição com drag and drop em lista simples

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
- [ ] Long press em qualquer componente entra no modo edição com transição suave
- [ ] Bottom nav desaparece ao entrar em edit mode
- [ ] Toolbar de edição aparece (Cancelar | Editar | Confirmar)
- [ ] Componentes são arrastáveis por long press + drag (o componente inteiro, sem ícone de handle)
- [ ] A ordem dos componentes muda visualmente durante o drag
- [ ] Tap em um componente abre a modal de opções com "Remover"
- [ ] "Remover" remove o componente da lista com animação de saída
- [ ] "Confirmar" persiste a nova ordem e composição — ao reabrir o app a ordem é mantida
- [ ] "Cancelar" descarta as alterações e restaura o estado anterior

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
- [ ] Cada componente em edit mode parece o componente real com dados de exemplo
- [ ] `TotalBalance` mostra valor, `CreditCardsPager` mostra carrossel de cartões, etc.
- [ ] Nenhum componente é representado apenas por seu título/nome
- [ ] A transição de Viewing → Editing parece que os componentes "ficaram no lugar" enquanto o modo mudou
- [ ] Componentes não respondem a interações próprias em edit mode (frozen)
- [ ] O drag continua funcionando corretamente com os componentes reais renderizados
- [ ] Drag shadow visível durante o arrasto

> **Reprovação imediata:** qualquer componente que em edit mode exiba apenas texto/título em vez do visual original.

---

## Etapa 3 — Adicionar componentes

**Foco:** painel de adição com preview em miniatura e drag cross-container.

### Pré-requisito
Etapa 2 aprovada.

### O que entra

- `DashboardAction.AddComponent(key, insertAt)` — action de adição
- `DashboardViewModel.addComponent()` — move de `availableItems` → `items` na posição correta
- `AddComponentPanel` — overlay in-tree (não é `ModalBottomSheet`):
  - `AnimatedVisibility(slideInVertically / slideOutVertically)`
  - `LazyVerticalGrid` de 2 colunas com `DashboardAddItemCard`
  - Ocupa ~50% da tela; lista editável visível acima
- `DashboardAddItemCard` — card de item disponível:
  - Preview em miniatura do componente real (`DashboardComponentContent` em escala reduzida)
  - Título abaixo
  - Tap → `AddComponent(key, insertAt = null)` (adiciona no final)
  - Long press → inicia drag cross-container
- `DragToAddState` — estado de drag cross-container (CompositionLocal):
  - `isDragging`, `draggedKey`, `dragOffset`, `dropTargetIndex`
  - A lista acima calcula `dropTargetIndex` durante o drag
  - Indicador visual de drop (espaço reservado ou linha) na posição alvo
- Ghost preview flutuante durante drag cross-container
- Botão "+" na barra de edição que abre/fecha o painel

### O que NÃO entra
- Configurações de componentes

### Critérios de aceite
- [ ] Botão "+" na barra de edição abre o painel com animação de slide
- [ ] Componentes disponíveis são exibidos como miniatura do componente real (não só títulos)
- [ ] Tap em um componente disponível o adiciona ao final da lista
- [ ] Long press + drag de um componente do painel → drag cross-container funciona
- [ ] Durante o drag, a lista acima mostra onde o componente será inserido
- [ ] Ao soltar, o componente é inserido na posição indicada
- [ ] Componentes já presentes na dashboard não aparecem no painel
- [ ] Painel fecha ao confirmar ou cancelar o edit mode

---

## Etapa 4 — Configurações de componentes

**Foco:** modal de configuração por componente, com persistência dos configs.

### Pré-requisito
Etapa 3 aprovada.

### O que entra

**Domain:**
- `DashboardComponentPreference.config: Map<String, String>` — já no model desde Etapa 1, mas não usado
- Config constants por componente:
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
- `DashboardComponentOptionsModal` — expandida com seções de configuração por componente:
  - AccountsOverview: lista de contas com toggle
  - CreditCardsPager: lista de cartões com toggle
  - SpendingPager: segmented button (3 / 5 / 10 / Todas)
  - PendingRecurring: segmented button (7 / 14 / 30 dias)
  - Recents: segmented button (4 / 6 / 8 / 10)
  - QuickActions: lista de 7 ações com toggle (mínimo 1 visível)
- `DashboardComponentsBuilder` — lê `config` ao construir cada componente

### Critérios de aceite
- [ ] Tap em um componente com configuração disponível exibe as opções na modal
- [ ] Componentes sem configuração exibem apenas "Remover"
- [ ] QuickActions: desativar uma ação a remove do componente na dashboard
- [ ] QuickActions: não é possível desativar todas as ações (validação na modal)
- [ ] Recents: alterar o número de itens reflete imediatamente ao confirmar o edit mode
- [ ] PendingRecurring: alterar o horizonte de dias reflete ao confirmar
- [ ] SpendingPager: alterar o limite de categorias reflete ao confirmar
- [ ] AccountsOverview: excluir uma conta a remove do componente
- [ ] CreditCardsPager: excluir um cartão o remove do componente
- [ ] Todas as configurações persistem entre sessões do app