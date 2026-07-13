## ADDED Requirements

### Requirement: Tela ancorada numa entidade retorna quando a entidade é excluída

Uma tela de navegação cheia ancorada numa única entidade por id (ex.: faturas de um cartão, detalhe de uma issue de suporte) SHALL retornar automaticamente (`onNavigateBack`) quando a entidade observada deixar de existir, em vez de congelar, crashar ou permanecer em estado de carregamento indefinido. A observação da entidade MUST NOT descartar silenciosamente o `null` de forma que trave o fluxo de estado da tela. Telas que observam uma **coleção** (master-detail) MUST NOT crashar quando a coleção esvazia — SHALL degradar para um estado vazio.

#### Scenario: Cartão excluído com a tela de faturas aberta
- **WHEN** a tela de faturas de um cartão está aberta e esse cartão é excluído (por qualquer caminho, inclusive o menu da própria tela)
- **THEN** a tela retorna para a origem sem congelar nem exibir dados do cartão inexistente

#### Scenario: Coleção esvazia com a tela aberta
- **WHEN** uma tela master-detail observa uma coleção e todos os itens são excluídos enquanto ela está aberta
- **THEN** a tela apresenta um estado vazio, sem lançar exceção
