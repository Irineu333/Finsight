## ADDED Requirements

### Requirement: Detalhes pane-only distintos de detalhes sheet-capable

O mecanismo do painel SHALL distinguir dois tipos de detalhe adaptativo: **sheet-capable** e **pane-only**. Os detalhes `view*` e as configurações do widget do dashboard SHALL ser **sheet-capable** — em janela larga são exibidos no painel e, abaixo do breakpoint, rebaixados a `ModalBottomSheet` (comportamento inalterado). Um detalhe **pane-only** SHALL ser exibido **exclusivamente** no painel de detalhe (janela extra-larga) e MUST NOT ser rebaixado a `ModalBottomSheet`. Quando a janela deixa de ser extra-larga, um detalhe pane-only ativo SHALL ser **dispensado** pelo host, retornando o painel ao empty-state, em vez de transformado em bottom sheet. A distinção SHALL ser uma propriedade do próprio detalhe, para que o host escolha a apresentação sem conhecer a feature.

#### Scenario: Detalhe sheet-capable em janela estreita
- **WHEN** um detalhe sheet-capable (`view*` ou configurações do widget) está ativo e a janela está abaixo do breakpoint
- **THEN** ele é exibido como `ModalBottomSheet`, como hoje

#### Scenario: Detalhe pane-only em janela extra-larga
- **WHEN** um detalhe pane-only é aberto e a janela é extra-larga
- **THEN** ele é exibido no painel de detalhe à direita

#### Scenario: Detalhe pane-only ao sair de extra-larga
- **WHEN** um detalhe pane-only está ativo no painel e a janela é redimensionada para uma largura não extra-larga
- **THEN** o detalhe é dispensado e o painel volta ao empty-state, sem ser rebaixado a `ModalBottomSheet`
