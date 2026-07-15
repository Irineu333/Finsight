## 1. Distinção pane-only no mecanismo do painel (`core/designsystem`)

- [x] 1.1 Em `AdaptiveDetail.kt`, introduzir a distinção sheet-capable vs pane-only (propriedade do detalhe — ex.: hierarquia selada `AdaptiveDetail` com `AdaptiveModal` sheet-capable + um detalhe pane-only, ou flag equivalente), mantendo o `DetailPaneController` genérico
- [x] 1.2 Prover no detalhe pane-only o contrato full-bleed (`RenderFullBleed()` dono do layout) distinto do wrapper `RenderBody()`/`RenderActions()` dos `view*`, reutilizando a casca do painel (largura, `VerticalDivider`, botão fechar, empty-state)
- [x] 1.3 No `DetailPane`, ramificar a renderização: `view*` mantém wrapper de scroll + ações fixas; pane-only renderiza full-bleed sem esse wrapper
- [x] 1.4 No `DetailSheetHost`/`DetailPaneHost`, ignorar detalhes pane-only (não rebaixar a `ModalBottomSheet`) e **dispensar** um pane-only ativo quando a janela deixa de ser extra-larga

## 2. UI da conversa compartilhada (`feature/support/impl`)

- [x] 2.1 Extrair de `SupportIssueScreen` um composable `ChatContent` (cabeçalho da issue, lista de mensagens com divisores de dia e auto-scroll para a última, `ReplyComposer`), parametrizado pelo `SupportIssueViewModel`
- [x] 2.2 Refatorar `SupportIssueScreen` (rota) para envolver `ChatContent` no `Scaffold` (topbar com voltar + bottomBar), preservando o comportamento atual de navegação e evento `IssueDeleted`

## 3. `ChatDetail` no painel (`feature/support/impl`)

- [x] 3.1 Criar `ChatDetail(issueId)` como detalhe pane-only (`ViewModelStoreOwner`) que hospeda o `SupportIssueViewModel(issueId)` e renderiza `ChatContent` full-bleed com o composer fixo no rodapé do painel
- [x] 3.2 Tratar `IssueDeleted` no `ChatDetail` dispensando o detalhe do painel (`controller.dismiss()`), equivalente ao `onNavigateBack` da rota

## 4. Decisão de abertura no clique (`feature/support/impl`)

- [x] 4.1 Em `SupportScreen`, mover a decisão para `onOpenIssue`: `if (isExtraWideWindow()) controller.show(ChatDetail(id)) else navController.navigate(SupportIssueRoute(id))`, obtendo `LocalDetailPaneController` e a largura na própria tela
- [x] 4.2 Manter a rota `SupportIssueRoute` no grafo para o caso de navegação (janela não extra-larga); confirmar que o wide não navega
- [x] 4.3 Dispensar o `ChatDetail` ao sair da feature de suporte (a `SupportScreen` deixa a composição), evitando que o chat persista no painel app-scoped ao navegar para outra feature

## 5. Ajuste visual do painel pane-only (`core/designsystem`)

- [x] 5.1 No `DetailPane`, renderizar o detalhe pane-only sobre `colorScheme.background` (como a rota em tela cheia) para os cards do chat (`surface`) contrastarem, mantendo os `view*` sobre `colorScheme.surface`
- [x] 5.2 Suavizar o `VerticalDivider` do painel para `outlineVariant` a 50% de alpha, evitando contraste excessivo contra o novo fundo

## 6. Verificação

- [ ] 6.1 Extra-larga: abrir issue mostra o chat no painel com auto-scroll e composer no rodapé; fechar (X) volta ao empty-state
- [ ] 6.2 Não extra-larga: abrir issue navega para tela cheia com a transição do NavHost, como hoje
- [ ] 6.3 Resize: chat no painel some ao sair de extra-larga (não vira bottom sheet); chat na rota permanece tela cheia ao entrar em extra-larga (painel mostra empty-state)
- [ ] 6.4 `view*` inalterados: continuam painel em largo e `ModalBottomSheet` em estreito; enviar/excluir mensagem e `IssueDeleted` funcionam nas duas apresentações do chat
- [ ] 6.5 Rodar `./gradlew check`
