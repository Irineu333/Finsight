## ADDED Requirements

### Requirement: A página exibida e o escopo da tela nunca divergem

Numa tela cujo `HorizontalPager` seleciona o item que governa o restante do conteúdo — cartão, conta ou equivalente —, a página exibida pelo pager e o item descrito pelo restante da tela SHALL ser sempre o mesmo. MUST NOT ocorrer de o pager exibir um item enquanto os valores, ações e listas abaixo dele descrevem outro.

Essa garantia SHALL valer para toda sequência de navegação entre páginas, incluindo o retorno a uma página já visitada e o retorno à página inicial.

#### Scenario: Retorno à página inicial
- **WHEN** o usuário desliza da página inicial para outra e volta para a inicial
- **THEN** a fatura, as ações e a lista de transações voltam a descrever o cartão da página inicial

#### Scenario: Vaivém entre duas páginas
- **WHEN** o usuário alterna repetidamente entre duas páginas do pager
- **THEN** a cada parada o restante da tela descreve o cartão da página em que o pager parou

#### Scenario: Tela aberta com um item inicial
- **WHEN** a tela é aberta com um item inicial que não é o primeiro da lista, o usuário navega para outra página e volta para a inicial
- **THEN** o restante da tela volta a descrever o item inicial

### Requirement: O pager é a fonte da verdade da seleção que ele dirige

Enquanto o pager é o único controle que altera a seleção, ele SHALL ser a fonte da verdade dela: toda parada em uma página SHALL ser notificada ao estado da tela, e a deduplicação de notificações SHALL ser feita sobre a própria sequência de páginas do pager.

A tela MUST NOT condicionar essa notificação a uma comparação com o estado externo lido no momento em que o efeito foi lançado. Um valor capturado por um efeito de chave constante não acompanha as recomposições, e uma guarda escrita sobre ele silencia justamente as mudanças que deveria deixar passar.

#### Scenario: Notificação a cada parada
- **WHEN** o pager para em uma página diferente da anterior
- **THEN** a seleção da tela é atualizada para essa página

#### Scenario: Sem notificação redundante
- **WHEN** o pager é recomposto sem que a página corrente tenha mudado
- **THEN** nenhuma nova notificação de seleção é emitida

### Requirement: Telas com o mesmo contrato de seleção compartilham a mesma implementação

Telas que exibem uma coleção em pager e derivam o restante do conteúdo do item selecionado SHALL ligar pager e estado da mesma forma. Uma delas MUST NOT introduzir uma guarda ou uma condição que a outra não tenha, sem que a diferença esteja declarada como requisito.

#### Scenario: Cartões e contas
- **WHEN** o efeito que liga o pager à seleção é comparado entre a tela de cartões e a de contas
- **THEN** os dois expressam a mesma regra: notificar toda parada, deduplicando pela sequência de páginas
