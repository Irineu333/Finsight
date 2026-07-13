## ADDED Requirements

### Requirement: Teardown de overlays inclui o painel de detalhe

O teardown de todos os overlays transitórios (`ModalManager.dismissAll()`) SHALL também dispensar o detalhe atual do `DetailPaneController`. Um detalhe `view*` aberto (painel em janela larga ou bottom sheet em janela estreita) MUST NOT sobreviver a um `dismissAll()`. Quando nenhum detalhe está aberto, o teardown SHALL ser um no-op sobre o painel. Os fluxos que disparam o teardown (exclusões e submissões de formulário) MUST NOT precisar referenciar o `DetailPaneController` diretamente.

#### Scenario: Excluir a entidade de dentro do detalhe fecha o detalhe
- **WHEN** o usuário exclui uma entidade a partir do seu detalhe `view*` e a confirmação chama `dismissAll()`
- **THEN** tanto a confirmação quanto o detalhe (painel ou sheet) são dispensados, e o painel retorna ao empty-state em janela larga

#### Scenario: Teardown sem detalhe aberto
- **WHEN** `dismissAll()` é chamado e nenhum detalhe está aberto no `DetailPaneController`
- **THEN** apenas os modais transitórios são dispensados, sem efeito sobre o painel

#### Scenario: Submeter formulário a partir do detalhe fecha o detalhe
- **WHEN** o usuário salva uma edição aberta a partir de um detalhe `view*` e o form ViewModel chama `dismissAll()`
- **THEN** o formulário e o detalhe são dispensados juntos, evitando exibição de dados obsoletos no detalhe
