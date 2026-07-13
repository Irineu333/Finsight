## Context

O app já possui um mecanismo de apresentação adaptativa (`adaptive-detail-pane`):

- `AdaptiveModal` (`core/designsystem`) — base para superfícies que se adaptam à largura da janela.
- `DetailPaneController` — slot único; `show(modal)` / `dismiss()`.
- `DetailPaneHost` provê `LocalDetailPaneController` e monta o `DetailSheetHost` (renderiza como `ModalBottomSheet` quando **não** é `isExtraWideWindow()`).
- `DetailPane` (`ChromeHost`) — painel fixo à direita, renderizado quando `isExtraWideWindow()`; provê botão de fechar (X), rolagem e empty-state.

Hoje os detalhes `view*` (categoria, orçamento, operação, ajuste, recorrência) já usam esse mecanismo via `detailController.show(...)`. Já a **configuração do widget do dashboard** (`DashboardComponentOptionsModal`) ainda é um `ModalBottomSheet` aberto por `modalManager.show(...)` em `DashboardEditingContent`, então em janela larga aparece embaixo, ignorando o painel.

No modo de edição, o dashboard publica `ChromeConfig.ContentOnly` (esconde rail/bottom bar), mas o `DetailPane` continua sendo renderizado pelo `ChromeHost` sempre que `isExtraWideWindow()` — portanto o painel está disponível durante a edição.

## Goals / Non-Goals

**Goals:**
- Reaproveitar o mecanismo `AdaptiveModal` + `DetailPaneController` existente para as configurações do widget.
- Em janela larga: configurações no painel à direita, com o dashboard editável visível à esquerda.
- Em janela estreita: manter o `ModalBottomSheet` atual, sem regressão.
- Preservar o estado da configuração ao cruzar o breakpoint (garantido pelo mecanismo existente).

**Non-Goals:**
- Tornar formulários/confirmações adaptativos.
- Alterar o conteúdo/opções das configurações de cada tipo de componente.
- Mudar a navegação, persistência de preferências ou o fluxo de confirmar/cancelar a edição do dashboard como um todo.

## Decisions

### Decisão 1 — `DashboardComponentOptionsModal` vira `AdaptiveModal`

Converter a classe de `ModalBottomSheet` (`ColumnScope.BottomSheetContent()`) para `AdaptiveModal` (`DetailContent()`). O corpo já começa com um `Column` próprio, então a migração é direta. A abertura em `DashboardEditingContent` troca `modalManager.show(...)` por `LocalDetailPaneController.current.show(...)`.

- **Por quê:** é exatamente o contrato que os `view*` já seguem; a casca decide painel-vs-sheet pela largura, e a feature apenas escolhe o mecanismo (`DetailPaneController`). Não introduz novo padrão.
- **Alternativa considerada:** manter `ModalBottomSheet` e ramificar por `isExtraWideWindow()` dentro da feature para montar um painel próprio. Rejeitada: duplicaria o mecanismo do painel e violaria "a feature não decide a superfície".

### Decisão 2 — Dispensa via `DetailPaneController`

Os botões **Cancelar** e **Confirmar** passam a chamar `LocalDetailPaneController.current.dismiss()` (em vez de `modalManager.dismiss()`). Confirmar continua emitindo `DashboardAction.UpdateComponentConfig(...)` antes de dispensar. Em janela larga o painel também oferece o X nativo (dispensa sem aplicar), coexistindo com o Cancelar do conteúdo.

- **Por quê:** a superfície agora vive no `DetailPaneController`; dispensá-la pelo `ModalManager` não a fecharia.

### Decisão 3 — Rolagem do conteúdo longo na apresentação em sheet

O conteúdo de configuração pode ser longo (lista de contas/cartões). Hoje o `DashboardComponentOptionsModal` faz seu próprio `verticalScroll`. Como `AdaptiveModal`, o `DetailPane` já envolve o conteúdo num `Column.verticalScroll`; um segundo `verticalScroll` interno (mesma orientação) sob restrição de altura infinita é problemático. Seguindo o padrão dos `view*` (que **não** rolam internamente e delegam ao container), o conteúdo remove seu `verticalScroll` interno. Para não perder rolagem na apresentação em **sheet**, o `DetailSheetHost` passa a envolver o conteúdo adaptativo num `Column.verticalScroll` (espelhando o `DetailPane`).

- **Por quê:** unifica o comportamento de rolagem entre painel e sheet para qualquer `AdaptiveModal` longo, corrigindo uma lacuna latente para todos os detalhes, não só o do widget.
- **Alternativa considerada:** manter o `verticalScroll` interno só no caminho de sheet. Rejeitada: exigiria a feature saber a superfície atual, contrariando o contrato.
- **Trade-off:** toca `core/designsystem` (`DetailSheetHost`), afetando todos os `AdaptiveModal`. Os `view*` atuais são curtos e continuam corretos com um container rolável.

## Risks / Trade-offs

- [Duplo `verticalScroll` causando erro de medição em janela larga] → Remover o `verticalScroll` interno do conteúdo e delegar ao container (painel/sheet).
- [Perda de rolagem no bottom sheet em telefones] → `DetailSheetHost` passa a prover rolagem, espelhando o `DetailPane`.
- [Mudança em `core/designsystem` afeta outros `AdaptiveModal`] → Baixo risco: envolver em `verticalScroll` é compatível com os `view*` curtos existentes; validar visualmente os detalhes em sheet estreito.
- [Painel indisponível durante a edição por causa de `ContentOnly`] → Não há risco: o `DetailPane` é renderizado pelo `ChromeHost` por `isExtraWideWindow()`, independente de `ChromeConfig`.

## Open Questions

- Nenhuma. O mecanismo adaptativo e os pontos de integração já existem; a mudança é reaproveitá-los para a superfície de configuração do widget.
