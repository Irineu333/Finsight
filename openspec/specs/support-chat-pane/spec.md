# support-chat-pane Specification

## Purpose
TBD - created by syncing change open-issue-on-panel. Update Purpose after archive.
## Requirements
### Requirement: Decisão painel-vs-navegação no momento da abertura

Ao abrir a conversa de uma issue de suporte, a apresentação SHALL ser decidida **no momento do clique** a partir da largura da janela, sem estado derivado nem sincronização contínua entre apresentações. Em janela **extra-larga**, o chat SHALL abrir no **painel de detalhe** à direita, via `DetailPaneController`. Caso contrário, o chat SHALL abrir como **tela cheia via navegação**, preservando a transição do NavHost. A decisão SHALL ser feita pela tela de lista do suporte (que tem acesso ao controlador do painel e à largura da janela), MUST NOT ser feita pelo shell, e MUST NOT depender de um `LaunchedEffect` que observe mudanças de largura.

#### Scenario: Abrir issue em janela extra-larga
- **WHEN** o usuário abre uma issue na lista de suporte e a janela é extra-larga
- **THEN** o chat é exibido no painel de detalhe à direita, e nenhuma navegação de rota é disparada

#### Scenario: Abrir issue em janela não extra-larga
- **WHEN** o usuário abre uma issue na lista de suporte e a janela não é extra-larga
- **THEN** o chat é aberto como tela cheia via navegação de rota, com a transição do NavHost, como hoje

### Requirement: UI da conversa compartilhada entre rota e painel

A UI da conversa (cabeçalho da issue, lista de mensagens com auto-scroll para a última e composer de resposta) SHALL ser um composable **comum**, consumido tanto pela tela de rota (`SupportIssueScreen`) quanto pelo detalhe do painel (`ChatDetail`). As duas apresentações MUST NOT duplicar a renderização das mensagens nem a lógica do composer.

#### Scenario: Mesma conversa nas duas apresentações
- **WHEN** a mesma issue é aberta no painel (extra-larga) e como tela cheia (não extra-larga)
- **THEN** ambas renderizam a conversa a partir do mesmo composable comum, exibindo cabeçalho, mensagens e composer de forma equivalente

### Requirement: Chat no painel é full-bleed e dono do próprio layout

Na apresentação em painel, o `ChatDetail` SHALL renderizar **full-bleed** — dono do seu próprio layout interno (cabeçalho, lista de mensagens rolável com auto-scroll para a última mensagem, e composer fixado embaixo). O `ChatDetail` MUST NOT usar o wrapper de rolagem top-aligned nem o rodapé de **ações** dos detalhes `view*`; o composer NÃO é tratado como "ações". A casca do painel (largura, divisória, botão de fechar, empty-state) SHALL ser reutilizada.

#### Scenario: Conversa renderizada full-bleed no painel
- **WHEN** o `ChatDetail` é exibido no painel
- **THEN** a lista de mensagens rola com auto-scroll para a última mensagem e o composer permanece fixo no rodapé do painel, sem o wrapper de rolagem nem o slot de ações dos detalhes `view*`

#### Scenario: Fechar o chat do painel
- **WHEN** o chat está aberto no painel e o usuário aciona o controle de fechar (X) da casca
- **THEN** o chat é dispensado e o painel volta ao empty-state, sem navegar nem fechar o app

### Requirement: Política de resize do chat

O comportamento ao redimensionar a janela SHALL ser explícito e sem transformação entre apresentações. Um chat aberto no **painel** que cruza para **fora** de extra-larga SHALL ser **fechado** (dispensado), e MUST NOT ser rebaixado a `ModalBottomSheet`. Um chat aberto como **rota** (tela cheia) SHALL **permanecer** tela cheia em qualquer redimensionamento, sem migrar para o painel. Não há preservação de instância nem de estado entre as apresentações.

#### Scenario: Painel encolhe para fora de extra-larga
- **WHEN** o chat está aberto no painel e a janela é redimensionada para uma largura não extra-larga
- **THEN** o chat do painel é fechado, sem virar bottom sheet

#### Scenario: Rota permanece em qualquer largura
- **WHEN** o chat está aberto como tela cheia via rota e a janela é redimensionada para extra-larga
- **THEN** o chat permanece tela cheia, sem migrar para o painel (o painel reservado à direita exibe seu empty-state)
