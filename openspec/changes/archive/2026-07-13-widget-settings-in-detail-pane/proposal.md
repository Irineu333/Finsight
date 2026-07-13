## Why

No modo de edição do dashboard, tocar num widget abre as configurações do componente (`DashboardComponentOptionsModal`) sempre como `ModalBottomSheet`, mesmo em janelas largas (desktop/paisagem) onde já existe um painel de detalhe reservado à direita. Isso gera uma experiência inconsistente: enquanto os detalhes `view*` já se adaptam ao painel, as configurações do widget continuam ancoradas embaixo, cobrindo o dashboard que o usuário está editando e desperdiçando o espaço do painel.

## What Changes

- As configurações do widget do modo de edição (`DashboardComponentOptionsModal`) passam a ser uma superfície **adaptativa**: em janela larga abrem no **painel de detalhe** à direita; em janela estreita permanecem como `ModalBottomSheet` (comportamento atual em telefones).
- A abertura no modo de edição passa a usar o `DetailPaneController` em vez do `ModalManager` para essa superfície.
- Em janela larga, o dashboard continua editável à esquerda enquanto as configurações do widget aparecem no painel, sem cobrir o conteúdo.
- Confirmar/cancelar as configurações dispensam a superfície pelo `DetailPaneController` (o painel volta ao empty-state; o sheet fecha).

## Capabilities

### New Capabilities
<!-- Nenhuma capability nova. -->

### Modified Capabilities
- `adaptive-detail-pane`: o escopo de superfícies adaptativas passa a incluir as **configurações do widget do dashboard** (modo de edição), além dos detalhes `view*`. Formulários e confirmações continuam como `ModalBottomSheet` em qualquer largura.

## Impact

- `feature/dashboard/impl` — `DashboardComponentOptionsModal` (passa de `ModalBottomSheet` para `AdaptiveModal`) e `DashboardEditingContent` (abre via `DetailPaneController`).
- `core/designsystem` — possível ajuste no `DetailSheetHost` para garantir rolagem do conteúdo adaptativo longo na apresentação em sheet (lista de contas/cartões).
- Sem mudança de dados, navegação ou APIs de feature. Comportamento em telefones preservado.
