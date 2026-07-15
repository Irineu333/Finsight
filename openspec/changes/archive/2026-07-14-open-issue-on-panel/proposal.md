## Why

Em janelas extra-largas (desktop) já existe um painel de detalhe reservado à direita, mas a conversa de uma issue de suporte continua abrindo como tela cheia via navegação, deixando o painel vazio e desperdiçando o espaço do layout master-detail. Abrir o chat da issue no painel aproveita a superfície já reservada e mantém a lista de issues visível ao lado da conversa.

## What Changes

- Ao abrir uma issue de suporte, a apresentação passa a ser **decidida no momento do clique** pela largura da janela: em janela **extra-larga** o chat abre no **painel de detalhe** à direita; caso contrário, abre como **tela cheia via navegação** (comportamento de hoje, com a transição do NavHost preservada).
- Introduz um detalhe de painel **pane-only** para o chat (`ChatDetail`), que renderiza *full-bleed* (header + lista de mensagens com auto-scroll + composer de resposta) — distinto dos detalhes `view*` que rolam dentro do container e ancoram ações fixas.
- A UI da conversa vira um composable comum consumido tanto pela tela de rota (`SupportIssueScreen`) quanto pelo `ChatDetail` do painel (DRY).
- O host do painel passa a distinguir detalhes **sheet-capable** (os `view*` de hoje, que viram `ModalBottomSheet` em janela estreita) de detalhes **pane-only** (o chat, que **não** vira bottom sheet): ao sair de extra-larga, um detalhe pane-only é **dispensado** em vez de rebaixado a sheet.
- Comportamento de resize explícito e simples: chat aberto no **painel** que cruza para fora de extra-larga → **fecha**; chat aberto como **rota** → permanece tela cheia, sem transformação. Não há fonte única de verdade nem adaptação instantânea entre as duas apresentações — a decisão é pontual.

## Capabilities

### New Capabilities
- `support-chat-pane`: apresentação da conversa de uma issue de suporte no painel de detalhe em janelas extra-largas, com decisão painel-vs-navegação no momento da abertura, UI de conversa compartilhada entre rota e painel, e política de resize (fechar o painel ao sair de extra-larga).

### Modified Capabilities
- `adaptive-detail-pane`: o mecanismo do painel passa a distinguir detalhes **sheet-capable** de **pane-only**. Detalhes pane-only são exibidos exclusivamente no painel (janela extra-larga) e são **dispensados** quando a janela deixa de ser extra-larga, em vez de rebaixados a `ModalBottomSheet`.

## Impact

- **Nova capability spec**: `support-chat-pane`.
- **Spec modificada**: `adaptive-detail-pane` (distinção sheet-capable vs pane-only no `DetailPaneController`/hosts).
- **`core/designsystem`** — `AdaptiveDetail.kt`: `AdaptiveModal`/`AdaptiveDetail` ganham a distinção pane-only vs sheet-capable; `DetailSheetHost`/`DetailPaneHost` passam a dispensar detalhes pane-only ao sair de extra-larga.
- **`feature/support/impl`** — extrair `ChatContent` (UI comum), criar `ChatDetail : AdaptiveDetail` pane-only, e mover a decisão painel-vs-navegação para `SupportScreen` (lê a largura no clique). `SupportIssueScreen`/`supportGraph` continuam servindo o caso de navegação.
- Sem mudança de dados, repositório ou backend Firebase.
